package cl.antubank.transfer.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración del <strong>Outbox Relay</strong> (tarea 5.2, Requisito 5).
 *
 * <p>Controlan a qué topic de Kafka se publican los eventos de transferencia, con qué cadencia el
 * poller drena los eventos pendientes de la outbox y cuántos eventos se publican como máximo por
 * ciclo. Todas admiten override por variable de entorno (ver {@code application.yml}).
 *
 * @param topic         topic destino de los eventos (ej. {@code transfer.events}).
 * @param pollIntervalMs cadencia del poller en milisegundos.
 * @param batchSize     máximo de eventos a publicar por ciclo (acota el tamaño del lote).
 */
@ConfigurationProperties(prefix = "outbox.relay")
public record OutboxRelayProperties(String topic, long pollIntervalMs, int batchSize) {

    public OutboxRelayProperties {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("outbox.relay.topic no puede estar vacío");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("outbox.relay.batch-size debe ser positivo");
        }
    }
}
