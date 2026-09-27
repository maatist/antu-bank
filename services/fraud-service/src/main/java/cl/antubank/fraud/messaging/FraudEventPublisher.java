package cl.antubank.fraud.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica a Kafka los eventos {@link TransferFlaggedEvent} cuando el motor de reglas marca una
 * transferencia como sospechosa (tarea 7.1, Requisito 7, criterio 2).
 *
 * <p>Sigue la misma convención de mensajería que el outbox relay de transfer-service:
 * <ul>
 *   <li>La <strong>clave</strong> del mensaje es el {@code transferId}, para preservar el orden por
 *       partición de los eventos de una misma transferencia.</li>
 *   <li>El <strong>esquema versionado</strong> viaja en los headers {@code eventType} /
 *       {@code eventVersion}; el valor del mensaje es el JSON del payload.</li>
 * </ul>
 *
 * <p>El {@code KafkaTemplate<String, String>} lo provee la autoconfiguración de Spring Boot a
 * partir de {@code spring.kafka.producer.*}.
 */
@Component
public class FraudEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FraudEventPublisher.class);

    /** Header de Kafka con el tipo de evento (esquema versionado). */
    public static final String HEADER_EVENT_TYPE = "eventType";

    /** Header de Kafka con la versión del esquema del evento. */
    public static final String HEADER_EVENT_VERSION = "eventVersion";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final FraudEventProperties properties;

    public FraudEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper,
                               FraudEventProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Serializa y publica un evento {@code TransferFlagged}.
     *
     * @param event evento a publicar.
     */
    public void publish(TransferFlaggedEvent event) {
        String key = event.transferId().toString();
        ProducerRecord<String, String> record =
                new ProducerRecord<>(properties.topic(), key, serialize(event));

        Headers headers = record.headers();
        headers.add(HEADER_EVENT_TYPE,
                TransferFlaggedEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        headers.add(HEADER_EVENT_VERSION,
                Integer.toString(TransferFlaggedEvent.EVENT_VERSION).getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record);
        log.info("Transferencia {} marcada como sospechosa (motivo={}); evento TransferFlagged "
                + "publicado en {}", event.transferId(), event.reason(), properties.topic());
    }

    private String serialize(TransferFlaggedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Un payload serializable con campos simples no debería fallar: error de programación.
            throw new IllegalStateException("No se pudo serializar el evento TransferFlagged", e);
        }
    }
}
