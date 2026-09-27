package cl.antubank.fraud.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cl.antubank.domain.money.Currency;
import cl.antubank.fraud.rules.FraudReason;
import cl.antubank.fraud.rules.FraudRuleEngine;
import cl.antubank.fraud.rules.FraudRulesProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Test de <em>wiring</em> del consumer: verifica el enrutamiento por header {@code eventType}, la
 * deserialización del JSON con la forma exacta que publica transfer-service, y que al dispararse una
 * regla se emita un {@link TransferFlaggedEvent} con los datos correctos (tarea 7.1, Requisito 7).
 *
 * <p>No levanta Kafka: invoca directamente el método del listener con un payload representativo. El
 * {@link FraudEventPublisher} se sustituye por un mock para inspeccionar el evento publicado.
 */
class TransferEventConsumerTest {

    private FraudEventPublisher publisher;
    private TransferEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Reloj fijo: irrelevante para estos casos (una sola transferencia por cuenta). */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        FraudRulesProperties props = new FraudRulesProperties(
                new FraudRulesProperties.HighAmount(5_000_000L),
                new FraudRulesProperties.Velocity(Duration.ofMinutes(1), 5, 10_000_000L));
        FraudRuleEngine engine = new FraudRuleEngine(props, FIXED_CLOCK);
        publisher = Mockito.mock(FraudEventPublisher.class);
        consumer = new TransferEventConsumer(engine, publisher, objectMapper);
    }

    /** JSON con la misma forma que serializa transfer-service (TransferConfirmedEvent). */
    private String payload(UUID transferId, UUID source, UUID destination, long amountMinor) {
        return """
                {
                  "transferId": "%s",
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amountMinor": %d,
                  "currency": "CLP"
                }
                """.formatted(transferId, source, destination, amountMinor);
    }

    private static byte[] eventType(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void transferenciaDeMontoAltoSeMarcaYSePublicaEvento() {
        UUID transferId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();

        consumer.onTransferEvent(
                payload(transferId, source, destination, 6_000_000L),
                eventType(TransferConfirmedEvent.EVENT_TYPE),
                transferId.toString());

        ArgumentCaptor<TransferFlaggedEvent> captor =
                ArgumentCaptor.forClass(TransferFlaggedEvent.class);
        verify(publisher).publish(captor.capture());

        TransferFlaggedEvent flagged = captor.getValue();
        assertThat(flagged.transferId()).isEqualTo(transferId);
        assertThat(flagged.sourceAccountId()).isEqualTo(source);
        assertThat(flagged.reason()).isEqualTo(FraudReason.HIGH_AMOUNT);
        assertThat(flagged.amountMinor()).isEqualTo(6_000_000L);
        assertThat(flagged.currency()).isEqualTo(Currency.CLP);
    }

    @Test
    void transferenciaNormalNoPublicaEvento() {
        UUID transferId = UUID.randomUUID();

        consumer.onTransferEvent(
                payload(transferId, UUID.randomUUID(), UUID.randomUUID(), 100_000L),
                eventType(TransferConfirmedEvent.EVENT_TYPE),
                transferId.toString());

        verify(publisher, never()).publish(any());
    }

    @Test
    void eventoDeOtroTipoSeIgnora() {
        UUID transferId = UUID.randomUUID();

        consumer.onTransferEvent(
                payload(transferId, UUID.randomUUID(), UUID.randomUUID(), 9_000_000L),
                eventType("OtroEvento"),
                transferId.toString());

        verify(publisher, never()).publish(any());
    }

    @Test
    void eventoSinHeaderEventTypeSeIgnora() {
        UUID transferId = UUID.randomUUID();

        consumer.onTransferEvent(
                payload(transferId, UUID.randomUUID(), UUID.randomUUID(), 9_000_000L),
                null,
                transferId.toString());

        verify(publisher, never()).publish(any());
    }
}
