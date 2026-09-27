package cl.antubank.transfer.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.antubank.domain.money.Currency;
import cl.antubank.transfer.AbstractPostgresIntegrationTest;
import cl.antubank.transfer.api.CreateTransferRequest;
import cl.antubank.transfer.outbox.ConfigurableLedgerConfig.ConfigurableLedgerClient;
import cl.antubank.transfer.persistence.IdempotencyKeyRepository;
import cl.antubank.transfer.persistence.TransferRepository;
import cl.antubank.transfer.persistence.TransferStatus;
import cl.antubank.transfer.service.InsufficientFundsException;
import cl.antubank.transfer.service.TransferResult;
import cl.antubank.transfer.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Tests de integración del <strong>Outbox pattern</strong> del transfer-service (tarea 5.1) contra
 * un PostgreSQL real (Testcontainers).
 *
 * <p>Verifican el Requisito 5:
 * <ul>
 *   <li><strong>Criterio 1 (misma transacción):</strong> al confirmar una transferencia se escribe
 *       un evento {@code TransferConfirmed} en la outbox <em>atómicamente</em> con la
 *       transferencia y su clave de idempotencia; y si la operación se revierte (fondos
 *       insuficientes, o fallo del ledger dentro de la transacción) NO queda ninguna fila de
 *       outbox (ni transferencia). Así se resuelve el dual-write DB↔Kafka.</li>
 *   <li><strong>Criterio 4 (versionado):</strong> el evento persistido lleva tipo y versión de
 *       esquema en columnas dedicadas, y el payload JSON contiene los datos de la transferencia.</li>
 * </ul>
 *
 * <p>Se ejercita {@link TransferService} directamente (sin capa HTTP) para observar el estado
 * transaccional de la base tras cada operación, usando un doble configurable del ledger
 * ({@link ConfigurableLedgerConfig}).
 */
@Import(ConfigurableLedgerConfig.class)
class OutboxIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    TransferService transferService;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    ConfigurableLedgerClient ledger;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void limpiar() {
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
        ledger.reset();
    }

    @AfterEach
    void limpiarDespues() {
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
    }

    private CreateTransferRequest request(UUID source, UUID destination, long amountMinor) {
        return new CreateTransferRequest(source, destination, amountMinor);
    }

    @Test
    void transferenciaConfirmadaEscribeEventoEnOutboxEnLaMismaTransaccion() throws Exception {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 50_000;
        ledger.setBalanceMinor(200_000);

        TransferResult result = transferService.transfer(
                "key-outbox-ok", request(source, destination, amount));

        // La transferencia se confirmó y se persistió junto a su clave de idempotencia.
        assertThat(result.replayed()).isFalse();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(transferRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);

        // Exactamente un evento en la outbox, pendiente de publicar y versionado (criterios 1 y 4).
        List<OutboxEventEntity> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEventEntity event = events.get(0);
        assertThat(event.getAggregateType()).isEqualTo("Transfer");
        assertThat(event.getAggregateId()).isEqualTo(result.transfer().getId());
        assertThat(event.getEventType()).isEqualTo(TransferConfirmedEvent.EVENT_TYPE);
        assertThat(event.getEventVersion()).isEqualTo(TransferConfirmedEvent.EVENT_VERSION);
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getOccurredAt()).isNotNull();

        // El payload JSON reconstituye los datos de negocio de la transferencia.
        TransferConfirmedEvent payload =
                objectMapper.readValue(event.getPayload(), TransferConfirmedEvent.class);
        assertThat(payload.transferId()).isEqualTo(result.transfer().getId());
        assertThat(payload.sourceAccountId()).isEqualTo(source);
        assertThat(payload.destinationAccountId()).isEqualTo(destination);
        assertThat(payload.amountMinor()).isEqualTo(amount);
        assertThat(payload.currency()).isEqualTo(Currency.CLP);
    }

    @Test
    void fondosInsuficientesNoEscribeEventoNiTransferencia() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        // Saldo menor al monto: la transferencia se rechaza antes de confirmarse.
        ledger.setBalanceMinor(10_000);

        assertThatThrownBy(() -> transferService.transfer(
                "key-outbox-sin-fondos", request(source, destination, 50_000)))
                .isInstanceOf(InsufficientFundsException.class);

        // Nada se persiste: ni transferencia, ni clave, ni evento de outbox (atomicidad).
        assertThat(transferRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(outboxEventRepository.count())
                .as("una transferencia rechazada no debe dejar evento en la outbox")
                .isZero();
    }

    @Test
    void fallaDelLedgerRevierteTransferenciaYEvento() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        ledger.setBalanceMinor(1_000_000);
        // El registro del asiento falla DENTRO de la transacción de negocio: todo debe revertirse.
        ledger.setFailOnRegister(true);

        assertThatThrownBy(() -> transferService.transfer(
                "key-outbox-ledger-falla", request(source, destination, 25_000)))
                .isInstanceOf(IllegalStateException.class);

        // Al revertirse la transacción, no queda transferencia, ni clave, ni evento: el evento de
        // outbox comparte la suerte de la escritura de negocio (Requisito 5, criterio 1).
        assertThat(transferRepository.count()).isZero();
        assertThat(idempotencyKeyRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void reintentoConMismaClaveNoDuplicaEventoEnOutbox() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 30_000;
        ledger.setBalanceMinor(500_000);
        CreateTransferRequest req = request(source, destination, amount);

        TransferResult first = transferService.transfer("key-outbox-repetida", req);
        TransferResult second = transferService.transfer("key-outbox-repetida", req);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.transfer().getId()).isEqualTo(first.transfer().getId());

        // El reintento reproduce el resultado sin volver a registrar en el ledger ni en la outbox:
        // un solo evento, correspondiente al primer procesamiento.
        assertThat(ledger.registerCallCount()).isEqualTo(1);
        assertThat(outboxEventRepository.count())
                .as("un reintento idempotente no debe duplicar el evento de outbox")
                .isEqualTo(1);
        assertThat(outboxEventRepository.findAll().get(0).getAggregateId())
                .isEqualTo(first.transfer().getId());
    }
}
