package cl.antubank.notification.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.antubank.domain.money.Currency;
import cl.antubank.notification.channel.Notification;
import cl.antubank.notification.channel.NotificationChannel;
import cl.antubank.notification.content.NotificationContentRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios del wiring de {@link NotificationEventConsumer} (tarea 7.2, Requisito 7,
 * criterios 3 y 4) con renderer y canal simulados (Mockito), sin infraestructura Kafka.
 *
 * <p>Verifican que un evento con el {@code eventType} correcto dispara el envio de una notificacion
 * por el canal, y que un evento con {@code eventType} ausente o distinto se ignora sin enviar.
 */
class NotificationEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private byte[] header(String value) {
        return value == null ? null : value.getBytes(StandardCharsets.UTF_8);
    }

    private Notification dummyNotification() {
        return new Notification("account:x", "asunto", "cuerpo", Locale.forLanguageTag("es-CL"));
    }

    @Test
    void transferConfirmedConHeaderCorrectoEnviaNotificacion() throws Exception {
        NotificationContentRenderer renderer = mock(NotificationContentRenderer.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        when(renderer.renderTransferConfirmed(any())).thenReturn(dummyNotification());

        NotificationEventConsumer consumer =
                new NotificationEventConsumer(renderer, channel, objectMapper);

        TransferConfirmedEvent event = new TransferConfirmedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 50_000L, Currency.CLP);
        String payload = objectMapper.writeValueAsString(event);

        consumer.onTransferEvent(payload, header("TransferConfirmed"), "key");

        verify(renderer).renderTransferConfirmed(any());
        verify(channel).send(any(Notification.class));
    }

    @Test
    void transferEventConEventTypeDistintoSeIgnora() throws Exception {
        NotificationContentRenderer renderer = mock(NotificationContentRenderer.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationEventConsumer consumer =
                new NotificationEventConsumer(renderer, channel, objectMapper);

        TransferConfirmedEvent event = new TransferConfirmedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 50_000L, Currency.CLP);
        String payload = objectMapper.writeValueAsString(event);

        // eventType ajeno: no debe procesarse.
        consumer.onTransferEvent(payload, header("OtroEvento"), "key");

        verify(channel, never()).send(any());
    }

    @Test
    void transferEventSinHeaderSeIgnora() throws Exception {
        NotificationContentRenderer renderer = mock(NotificationContentRenderer.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationEventConsumer consumer =
                new NotificationEventConsumer(renderer, channel, objectMapper);

        consumer.onTransferEvent("{}", header(null), "key");

        verify(channel, never()).send(any());
    }

    @Test
    void fraudEventConHeaderCorrectoEnviaNotificacion() throws Exception {
        NotificationContentRenderer renderer = mock(NotificationContentRenderer.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        when(renderer.renderTransferFlagged(any())).thenReturn(dummyNotification());

        NotificationEventConsumer consumer =
                new NotificationEventConsumer(renderer, channel, objectMapper);

        TransferFlaggedEvent event = new TransferFlaggedEvent(
                UUID.randomUUID(), UUID.randomUUID(), FraudReason.HIGH_AMOUNT, 9_000_000L,
                Currency.CLP);
        String payload = objectMapper.writeValueAsString(event);

        consumer.onFraudEvent(payload, header("TransferFlagged"), "key");

        verify(renderer).renderTransferFlagged(any());
        verify(channel).send(any(Notification.class));
    }

    @Test
    void fraudEventConEventTypeDistintoSeIgnora() throws Exception {
        NotificationContentRenderer renderer = mock(NotificationContentRenderer.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationEventConsumer consumer =
                new NotificationEventConsumer(renderer, channel, objectMapper);

        consumer.onFraudEvent("{}", header("TransferConfirmed"), "key");

        verify(channel, never()).send(any());
    }
}
