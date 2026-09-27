package cl.antubank.transfer.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.antubank.transfer.AbstractPostgresIntegrationTest;
import cl.antubank.transfer.api.CreateTransferRequest;
import cl.antubank.transfer.outbox.ConfigurableLedgerConfig;
import cl.antubank.transfer.outbox.ConfigurableLedgerConfig.ConfigurableLedgerClient;
import cl.antubank.transfer.outbox.OutboxEventRepository;
import cl.antubank.transfer.persistence.IdempotencyKeyRepository;
import cl.antubank.transfer.persistence.TransferEntity;
import cl.antubank.transfer.persistence.TransferRepository;
import cl.antubank.transfer.persistence.TransferStatus;
import cl.antubank.transfer.service.InsufficientFundsException;
import cl.antubank.transfer.service.TransferResult;
import cl.antubank.transfer.service.TransferService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Tests de integración de la <strong>compensación por paso</strong> de la saga (tarea 6.2) contra un
 * PostgreSQL real (Testcontainers), con el ledger simulado por un doble configurable.
 *
 * <p>Verifican el Requisito 6 en sus criterios de compensación:
 * <ul>
 *   <li><strong>Criterio 2 (compensar pasos completados):</strong> ante un fallo se ejecutan las
 *       compensaciones de los pasos ya completados, en orden inverso.</li>
 *   <li><strong>Criterio 3 (saldos íntegros):</strong> tras compensar, el efecto neto sobre los
 *       saldos es cero (como si la transferencia no hubiese ocurrido).</li>
 * </ul>
 *
 * <p>Escenarios cubiertos según design.md 6.3:
 * <ul>
 *   <li>fallo al <em>confirmar</em> (con el asiento ya registrado): se registra un asiento inverso
 *       en el ledger y la saga queda {@link SagaState#COMPENSADA} de forma durable, con la
 *       transferencia {@link TransferStatus#REJECTED} y los pasos asiento/reserva
 *       {@link SagaStepStatus#COMPENSADO};</li>
 *   <li>fallo al <em>reservar</em> fondos: no hay nada que compensar (no se registra reversa) y no
 *       queda estado local persistido;</li>
 *   <li>un reintento con la misma clave sobre una saga ya compensada reproduce el resultado
 *       rechazado sin aplicar ni compensar de nuevo (idempotencia).</li>
 * </ul>
 */
@Import({ConfigurableLedgerConfig.class,
        TransferSagaCompensationIntegrationTest.ConfirmacionConfigurableConfig.class})
class TransferSagaCompensationIntegrationTest extends AbstractPostgresIntegrationTest {

    /**
     * Doble configurable del paso de confirmación: permite forzar que la confirmación falle
     * <em>después</em> de un asentamiento exitoso, disparando la compensación con asiento inverso.
     */
    @TestConfiguration
    static class ConfirmacionConfigurableConfig {

        static final class ConfigurableConfirmationStep implements SagaConfirmationStep {
            private volatile boolean failOnConfirm = false;

            @Override
            public void confirmar(TransferEntity transfer) {
                if (failOnConfirm) {
                    throw new IllegalStateException(
                            "Fallo simulado al confirmar la transferencia " + transfer.getId());
                }
            }

            void setFailOnConfirm(boolean value) {
                this.failOnConfirm = value;
            }

            void reset() {
                this.failOnConfirm = false;
            }
        }

        @Bean
        @Primary
        ConfigurableConfirmationStep configurableConfirmationStep() {
            return new ConfigurableConfirmationStep();
        }
    }

    @Autowired
    TransferService transferService;

    @Autowired
    TransferSagaRepository sagaRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    ConfigurableLedgerClient ledger;

    @Autowired
    ConfirmacionConfigurableConfig.ConfigurableConfirmationStep confirmacion;

    @BeforeEach
    void limpiar() {
        sagaRepository.deleteAll();
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
        ledger.reset();
        confirmacion.reset();
    }

    @AfterEach
    void limpiarDespues() {
        sagaRepository.deleteAll();
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
        confirmacion.reset();
    }

    private CreateTransferRequest request(UUID source, UUID destination, long amountMinor) {
        return new CreateTransferRequest(source, destination, amountMinor);
    }

    @Test
    void falloAlConfirmarTrasAsentarCompensaConAsientoInversoYDejaSaldosIntegros() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 40_000;
        ledger.setBalanceMinor(1_000_000);
        // El asiento (asentar) tendrá éxito, pero la confirmación posterior fallará.
        confirmacion.setFailOnConfirm(true);

        // La saga compensa internamente y COMMITEA el estado compensado: no propaga excepción.
        TransferResult result = transferService.transfer(
                "key-saga-confirmar-falla", request(source, destination, amount));

        // La transferencia queda RECHAZADA: la operación fue compensada, no aplicada.
        assertThat(result.replayed()).isFalse();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);

        // Ledger: un asiento (asentar) + un asiento inverso (compensación). El ledger es inmutable,
        // por eso la reversa es un asiento nuevo, no un borrado (Requisito 3, criterio 3).
        assertThat(ledger.registerCallCount()).isEqualTo(1);
        assertThat(ledger.reverseCallCount()).isEqualTo(1);
        // Efecto neto sobre el saldo del origen: cero → saldos íntegros (Requisito 6, criterio 3).
        assertThat(ledger.netSourceEffectMinor()).isZero();

        // Estado de la saga persistido de forma durable (criterios 2 y 4).
        TransferSagaEntity saga = sagaRepository.findByTransferId(result.transfer().getId())
                .orElseThrow();
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSADA);
        // Compensación en orden inverso: reserva y asiento quedaron COMPENSADO; confirmación FALLIDO.
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.COMPENSADO);
        assertThat(saga.getPostStatus()).isEqualTo(SagaStepStatus.COMPENSADO);
        assertThat(saga.getConfirmStatus()).isEqualTo(SagaStepStatus.FALLIDO);
        assertThat(saga.getFailureReason()).contains("confirmar");

        // La transferencia rechazada, su clave y el evento de outbox quedan persistidos (durables).
        assertThat(transferRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
    }

    @Test
    void reintentoSobreSagaCompensadaReproduceRechazoSinCompensarDeNuevo() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        ledger.setBalanceMinor(1_000_000);
        confirmacion.setFailOnConfirm(true);
        CreateTransferRequest req = request(source, destination, 15_000);

        TransferResult first = transferService.transfer("key-saga-comp-repetida", req);
        TransferResult second = transferService.transfer("key-saga-comp-repetida", req);

        assertThat(first.replayed()).isFalse();
        assertThat(first.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);
        // El reintento reproduce el mismo resultado rechazado, sin nueva orquestación ni compensación.
        assertThat(second.replayed()).isTrue();
        assertThat(second.transfer().getId()).isEqualTo(first.transfer().getId());
        assertThat(second.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);

        // Una sola saga, un solo asiento y una sola reversa: la idempotencia impide duplicar.
        assertThat(sagaRepository.count()).isEqualTo(1);
        assertThat(ledger.registerCallCount()).isEqualTo(1);
        assertThat(ledger.reverseCallCount()).isEqualTo(1);
        assertThat(ledger.netSourceEffectMinor()).isZero();
    }

    @Test
    void falloAlReservarFondosNoCompensaNiDejaEstado() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        // Saldo insuficiente: la saga aborta al reservar, antes de asentar. Nada que compensar.
        ledger.setBalanceMinor(5_000);

        assertThatThrownBy(() -> transferService.transfer(
                "key-saga-comp-sin-fondos", request(source, destination, 50_000)))
                .isInstanceOf(InsufficientFundsException.class);

        // No hubo asiento ni reversa (design.md 6.3: ReservarFondos --> [*] nada que compensar).
        assertThat(ledger.registerCallCount()).isZero();
        assertThat(ledger.reverseCallCount()).isZero();
        assertThat(ledger.netSourceEffectMinor()).isZero();

        // El fallo ocurre antes de cualquier efecto externo: la transacción se revierte por completo.
        assertThat(transferRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(sagaRepository.count()).isZero();
    }
}
