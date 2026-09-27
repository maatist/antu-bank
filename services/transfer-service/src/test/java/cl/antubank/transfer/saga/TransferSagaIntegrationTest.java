package cl.antubank.transfer.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.antubank.transfer.AbstractPostgresIntegrationTest;
import cl.antubank.transfer.api.CreateTransferRequest;
import cl.antubank.transfer.outbox.ConfigurableLedgerConfig;
import cl.antubank.transfer.outbox.ConfigurableLedgerConfig.ConfigurableLedgerClient;
import cl.antubank.transfer.outbox.OutboxEventRepository;
import cl.antubank.transfer.persistence.IdempotencyKeyRepository;
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
import org.springframework.context.annotation.Import;

/**
 * Tests de integración de la <strong>saga de transferencia con estado persistido</strong> (tarea
 * 6.1) contra un PostgreSQL real (Testcontainers).
 *
 * <p>Verifican el Requisito 6 en su camino feliz:
 * <ul>
 *   <li><strong>Criterio 1 (pasos):</strong> la saga ejecuta reservar fondos → asentar → confirmar,
 *       dejando cada paso {@code COMPLETADO} y el estado global en {@link SagaState#CONFIRMADA}.</li>
 *   <li><strong>Criterio 4 (estado persistido):</strong> el estado de la saga queda en la tabla
 *       {@code transfer_saga} (una fila por transferencia), legible tras la operación.</li>
 * </ul>
 *
 * <p>Además se comprueba que el comportamiento observable de las tareas 4 y 5 se preserva: una
 * transferencia confirmada deja estado {@code CONFIRMED}, una fila de idempotencia y un evento de
 * outbox; el reintento idempotente no crea una segunda saga; y un rechazo por fondos insuficientes
 * no deja saga alguna (atomicidad).
 *
 * <p>Se ejercita {@link TransferService} directamente (sin capa HTTP) para observar el estado
 * transaccional de la base, usando el doble configurable del ledger ({@link ConfigurableLedgerConfig}).
 */
@Import(ConfigurableLedgerConfig.class)
class TransferSagaIntegrationTest extends AbstractPostgresIntegrationTest {

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

    @BeforeEach
    void limpiar() {
        sagaRepository.deleteAll();
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
        ledger.reset();
    }

    @AfterEach
    void limpiarDespues() {
        sagaRepository.deleteAll();
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
    }

    private CreateTransferRequest request(UUID source, UUID destination, long amountMinor) {
        return new CreateTransferRequest(source, destination, amountMinor);
    }

    @Test
    void caminoFelizPersisteSagaConfirmadaConTodosLosPasosCompletados() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 50_000;
        ledger.setBalanceMinor(200_000);

        TransferResult result = transferService.transfer(
                "key-saga-ok", request(source, destination, amount));

        // La transferencia terminó confirmada (criterio 1: confirmar es el paso final).
        assertThat(result.replayed()).isFalse();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.CONFIRMED);

        // Estado de la saga persistido (criterio 4): una única saga para esta transferencia.
        assertThat(sagaRepository.count()).isEqualTo(1);
        TransferSagaEntity saga = sagaRepository.findByTransferId(result.transfer().getId())
                .orElseThrow();
        assertThat(saga.getState()).isEqualTo(SagaState.CONFIRMADA);
        // Los tres pasos quedaron completados: reservar → asentar → confirmar (criterio 1).
        assertThat(saga.getReserveStatus()).isEqualTo(SagaStepStatus.COMPLETADO);
        assertThat(saga.getPostStatus()).isEqualTo(SagaStepStatus.COMPLETADO);
        assertThat(saga.getConfirmStatus()).isEqualTo(SagaStepStatus.COMPLETADO);
        assertThat(saga.getFailureReason()).isNull();
        assertThat(saga.getCreatedAt()).isNotNull();
        assertThat(saga.getUpdatedAt()).isNotNull();

        // Comportamiento de tareas 4 y 5 preservado: persistencia, idempotencia y outbox intactos.
        assertThat(transferRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        // La saga coordinó el asiento en el ledger exactamente una vez.
        assertThat(ledger.registerCallCount()).isEqualTo(1);
    }

    @Test
    void reintentoConMismaClaveNoCreaSegundaSaga() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 30_000;
        ledger.setBalanceMinor(500_000);
        CreateTransferRequest req = request(source, destination, amount);

        TransferResult first = transferService.transfer("key-saga-repetida", req);
        TransferResult second = transferService.transfer("key-saga-repetida", req);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transfer().getId()).isEqualTo(first.transfer().getId());

        // El reintento reproduce el resultado sin orquestar una nueva saga: sigue habiendo una sola.
        assertThat(sagaRepository.count()).isEqualTo(1);
        TransferSagaEntity saga = sagaRepository.findByTransferId(first.transfer().getId())
                .orElseThrow();
        assertThat(saga.getState()).isEqualTo(SagaState.CONFIRMADA);
        assertThat(ledger.registerCallCount()).isEqualTo(1);
    }

    @Test
    void fondosInsuficientesNoDejaSagaPersistida() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        // Saldo menor al monto: la saga aborta al reservar fondos, antes de asentar.
        ledger.setBalanceMinor(10_000);

        assertThatThrownBy(() -> transferService.transfer(
                "key-saga-sin-fondos", request(source, destination, 50_000)))
                .isInstanceOf(InsufficientFundsException.class);

        // Todo se revierte atómicamente: ni transferencia, ni clave, ni outbox, ni saga.
        assertThat(transferRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(sagaRepository.count())
                .as("una transferencia rechazada al reservar fondos no debe dejar saga")
                .isZero();
    }

    @Test
    void fallaAlAsentarRevierteLaSagaCompleta() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        ledger.setBalanceMinor(1_000_000);
        // El registro del asiento (paso asentar) falla DENTRO de la transacción: todo se revierte.
        ledger.setFailOnRegister(true);

        assertThatThrownBy(() -> transferService.transfer(
                "key-saga-asentar-falla", request(source, destination, 25_000)))
                .isInstanceOf(IllegalStateException.class);

        // Al compartir la transacción de negocio, el estado de la saga tampoco queda persistido.
        assertThat(transferRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(sagaRepository.count()).isZero();
    }
}
