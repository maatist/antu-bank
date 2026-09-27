package cl.antubank.fraud.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración de la publicación de eventos de fraude (tarea 7.1, Requisito 7,
 * criterio 2).
 *
 * @param topic topic destino de los eventos {@code TransferFlagged} (ej. {@code fraud.events}).
 */
@ConfigurationProperties(prefix = "fraud.events")
public record FraudEventProperties(String topic) {

    public FraudEventProperties {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("fraud.events.topic no puede estar vacío");
        }
    }
}
