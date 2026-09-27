package cl.antubank.fraud.messaging;

import cl.antubank.fraud.rules.FraudRuleEngine;
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
 * Consumer de Kafka que aplica las reglas de fraude a las transferencias confirmadas por
 * transfer-service (tarea 7.1, Requisito 7, criterios 1 y 2).
 *
 * <p><strong>Flujo.</strong> El relay de la outbox de transfer-service publica cada evento en el
 * topic {@code transfer.events} con el payload JSON como valor y el esquema versionado en los
 * headers {@code eventType} / {@code eventVersion}. Este listener:
 * <ol>
 *   <li>Filtra por el header {@code eventType}: solo procesa {@code TransferConfirmed} (otros tipos
 *       que compartan el topic se ignoran sin fallar).</li>
 *   <li>Deserializa el payload a {@link TransferConfirmedEvent}.</li>
 *   <li>Delega en {@link FraudRuleEngine} la evaluación de las reglas de monto y velocidad.</li>
 *   <li>Si alguna regla se dispara, emite un evento {@link TransferFlaggedEvent} vía
 *       {@link FraudEventPublisher}.</li>
 * </ol>
 *
 * <p>El servicio es stateless: no persiste; la ventana de velocidad vive en memoria en el motor.
 */
@Component
public class TransferEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);

    private final FraudRuleEngine ruleEngine;
    private final FraudEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public TransferEventConsumer(FraudRuleEngine ruleEngine,
                                 FraudEventPublisher publisher,
                                 ObjectMapper objectMapper) {
        this.ruleEngine = ruleEngine;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Escucha el topic de eventos de transferencia y evalúa las reglas de fraude.
     *
     * @param payload   valor del mensaje: JSON del evento.
     * @param eventType header con el tipo de evento (puede ser {@code null} en mensajes legados).
     * @param key       clave del mensaje (aggregateId = transferId), solo para trazas.
     */
    @KafkaListener(
            topics = "${fraud.events.transfer-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onTransferEvent(
            @Payload String payload,
            @Header(name = "eventType", required = false) byte[] eventType,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        String type = eventType == null ? null : new String(eventType, StandardCharsets.UTF_8);
        if (!TransferConfirmedEvent.EVENT_TYPE.equals(type)) {
            log.debug("Evento ignorado (eventType={}, key={}): no es TransferConfirmed", type, key);
            return;
        }

        TransferConfirmedEvent event = deserialize(payload);

        ruleEngine.evaluate(event.sourceAccountId(), event.amountMinor(), event.currency())
                .ifPresentOrElse(
                        reason -> publisher.publish(new TransferFlaggedEvent(
                                event.transferId(),
                                event.sourceAccountId(),
                                reason,
                                event.amountMinor(),
                                event.currency())),
                        () -> log.debug("Transferencia {} sin señales de fraude", event.transferId()));
    }

    private TransferConfirmedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, TransferConfirmedEvent.class);
        } catch (Exception e) {
            // Un payload ilegible es un error de contrato: se propaga para no confirmar el offset
            // silenciosamente ni procesar datos corruptos.
            throw new IllegalStateException(
                    "No se pudo deserializar el evento TransferConfirmed", e);
        }
    }
}
