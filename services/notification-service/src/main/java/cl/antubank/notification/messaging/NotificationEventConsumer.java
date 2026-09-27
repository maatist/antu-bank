package cl.antubank.notification.messaging;

import cl.antubank.notification.channel.Notification;
import cl.antubank.notification.channel.NotificationChannel;
import cl.antubank.notification.content.NotificationContentRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumer de Kafka que reacciona a los <strong>eventos notificables</strong> y dispara la
 * notificación al usuario (tarea 7.2, Requisito 7, criterios 3 y 4).
 *
 * <p><strong>Flujo.</strong> Escucha dos topics —{@code transfer.events} (evento
 * {@code TransferConfirmed} de transfer-service) y {@code fraud.events} (evento
 * {@code TransferFlagged} de fraud-service)— con el payload JSON como valor y el esquema versionado
 * en los headers {@code eventType} / {@code eventVersion}. Cada listener:
 * <ol>
 *   <li>Filtra por el header {@code eventType} (otros tipos que compartan el topic se ignoran sin
 *       fallar), igual que los consumers de fraud-service y ledger-service.</li>
 *   <li>Deserializa el payload al DTO del lado del consumer.</li>
 *   <li>Renderiza el contenido localizado (es/en) vía {@link NotificationContentRenderer}.</li>
 *   <li>Entrega la notificación por el {@link NotificationChannel} mock, que envía de forma
 *       <strong>asíncrona</strong> ({@code @Async}) para no bloquear al hilo consumidor.</li>
 * </ol>
 *
 * <p>El servicio es stateless: no persiste. Cada evento genera una notificación mock (log/email).
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationContentRenderer renderer;
    private final NotificationChannel channel;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(NotificationContentRenderer renderer,
                                     NotificationChannel channel,
                                     ObjectMapper objectMapper) {
        this.renderer = renderer;
        this.channel = channel;
        this.objectMapper = objectMapper;
    }

    /**
     * Escucha el topic de eventos de transferencia y notifica las transferencias completadas.
     *
     * @param payload   valor del mensaje: JSON del evento.
     * @param eventType header con el tipo de evento (puede ser {@code null} en mensajes legados).
     * @param key       clave del mensaje (transferId), solo para trazas.
     */
    @KafkaListener(
            topics = "${notification.events.transfer-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onTransferEvent(
            @Payload String payload,
            @Header(name = "eventType", required = false) byte[] eventType,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        if (!isType(eventType, TransferConfirmedEvent.EVENT_TYPE)) {
            log.debug("Evento ignorado (eventType={}, key={}): no es TransferConfirmed",
                    typeAsString(eventType), key);
            return;
        }

        TransferConfirmedEvent event =
                deserialize(payload, TransferConfirmedEvent.class, TransferConfirmedEvent.EVENT_TYPE);
        Notification notification = renderer.renderTransferConfirmed(event);
        channel.send(notification);
        log.info("Notificación de transferencia completada encolada para {} (transferId={})",
                notification.recipient(), event.transferId());
    }

    /**
     * Escucha el topic de eventos de fraude y notifica las transferencias marcadas como sospechosas.
     *
     * @param payload   valor del mensaje: JSON del evento.
     * @param eventType header con el tipo de evento (puede ser {@code null} en mensajes legados).
     * @param key       clave del mensaje (transferId), solo para trazas.
     */
    @KafkaListener(
            topics = "${notification.events.fraud-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onFraudEvent(
            @Payload String payload,
            @Header(name = "eventType", required = false) byte[] eventType,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        if (!isType(eventType, TransferFlaggedEvent.EVENT_TYPE)) {
            log.debug("Evento ignorado (eventType={}, key={}): no es TransferFlagged",
                    typeAsString(eventType), key);
            return;
        }

        TransferFlaggedEvent event =
                deserialize(payload, TransferFlaggedEvent.class, TransferFlaggedEvent.EVENT_TYPE);
        Notification notification = renderer.renderTransferFlagged(event);
        channel.send(notification);
        log.info("Notificación de transferencia sospechosa encolada para {} (transferId={}, motivo={})",
                notification.recipient(), event.transferId(), event.reason());
    }

    private static boolean isType(byte[] eventType, String expected) {
        return expected.equals(typeAsString(eventType));
    }

    private static String typeAsString(byte[] eventType) {
        return eventType == null ? null : new String(eventType, StandardCharsets.UTF_8);
    }

    private <T> T deserialize(String payload, Class<T> type, String eventType) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception e) {
            // Un payload ilegible es un error de contrato: se propaga para no confirmar el offset
            // silenciosamente ni notificar con datos corruptos.
            throw new IllegalStateException(
                    "No se pudo deserializar el evento " + eventType, e);
        }
    }
}
