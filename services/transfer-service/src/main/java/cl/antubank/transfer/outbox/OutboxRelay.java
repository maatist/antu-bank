package cl.antubank.transfer.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <strong>Outbox Relay</strong>: publica a Kafka los eventos pendientes de la tabla {@code outbox}
 * y los marca como publicados (tarea 5.2; ver requirements.md, Requisito 5, criterios 2 y 4, y
 * design.md, sección 6.2).
 *
 * <p><strong>Funcionamiento.</strong> Un poller ({@link #poll()}, disparado por {@link Scheduled})
 * lee periódicamente las filas con {@code published = false} ordenadas por {@code occurredAt}
 * ascendente (preservando el orden de ocurrencia) y, por cada una:
 * <ol>
 *   <li>Publica el evento a Kafka en el topic configurado, usando el {@code aggregateId} como
 *       <em>clave de mensaje</em> para que todos los eventos de una misma transferencia caigan en
 *       la misma partición y conserven su orden.</li>
 *   <li>Adjunta el <strong>esquema versionado</strong> del evento como headers de Kafka
 *       ({@code eventType} / {@code eventVersion}), de modo que los consumidores puedan enrutar y
 *       deserializar el payload sin ambigüedad (Requisito 5, criterio 4). El valor del mensaje es
 *       el JSON ya almacenado en la fila de outbox.</li>
 *   <li>Solo tras confirmar la publicación (ack del broker) marca la fila como publicada fijando
 *       {@code published_at}.</li>
 * </ol>
 *
 * <p><strong>Robustez.</strong> Si la publicación de un evento falla, la fila se deja
 * <em>sin marcar</em> (queda pendiente) para reintentarse en el siguiente ciclo, y se detiene el
 * procesamiento del lote actual para no romper el orden relativo de los eventos siguientes. Esto,
 * combinado con la idempotencia del producer y con la deduplicación por {@code id} aguas abajo, da
 * una entrega <em>al menos una vez</em> sin pérdida (Requisito 5): un evento nunca se marca como
 * publicado si no llegó a Kafka.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** Header de Kafka con el tipo de evento (esquema versionado, Requisito 5, criterio 4). */
    public static final String HEADER_EVENT_TYPE = "eventType";

    /** Header de Kafka con la versión del esquema del evento. */
    public static final String HEADER_EVENT_VERSION = "eventVersion";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxRelayProperties properties;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       OutboxRelayProperties properties) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /**
     * Poller programado que drena los eventos pendientes de la outbox hacia Kafka.
     *
     * <p>La cadencia se configura con {@code outbox.relay.poll-interval-ms}. Cualquier excepción se
     * captura para no detener el scheduler; los eventos no publicados quedan pendientes para el
     * siguiente ciclo.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms}")
    public void poll() {
        try {
            int published = publishPending();
            if (published > 0 && log.isDebugEnabled()) {
                log.debug("Outbox relay publicó {} evento(s) pendiente(s) a Kafka", published);
            }
        } catch (Exception e) {
            // No propagar: el scheduler debe seguir vivo. Los pendientes se reintentan luego.
            log.warn("Fallo en el ciclo del outbox relay; se reintentará en el próximo ciclo", e);
        }
    }

    /**
     * Publica a Kafka los eventos pendientes (hasta {@code batch-size}) en orden de ocurrencia y
     * marca como publicados solo los que se confirmaron.
     *
     * <p>Se ejecuta en una transacción: la lectura de pendientes y la marca de publicados forman
     * una unidad. Ante el primer fallo de publicación se interrumpe el lote (los eventos ya
     * marcados en esta transacción se confirman; el que falló y los posteriores quedan pendientes),
     * preservando el orden relativo.
     *
     * @return número de eventos publicados y marcados en este ciclo.
     */
    @Transactional
    public int publishPending() {
        List<OutboxEventEntity> pending =
                outboxEventRepository.findByPublishedFalseOrderByOccurredAtAsc();
        if (pending.isEmpty()) {
            return 0;
        }

        int published = 0;
        for (OutboxEventEntity event : pending) {
            if (published >= properties.batchSize()) {
                break;
            }
            try {
                publish(event);
            } catch (Exception e) {
                // La fila queda sin marcar (published = false) para reintentar. Se detiene el lote
                // para no publicar eventos posteriores antes que este (orden por partición).
                log.warn("No se pudo publicar el evento de outbox id={} (tipo={}); "
                                + "queda pendiente para reintento",
                        event.getId(), event.getEventType(), e);
                break;
            }
            event.markPublished(Instant.now());
            published++;
        }
        return published;
    }

    /**
     * Publica un evento individual a Kafka de forma síncrona (espera el ack del broker) para poder
     * marcar la fila como publicada solo si la entrega se confirmó.
     */
    private void publish(OutboxEventEntity event) throws InterruptedException, ExecutionException {
        // Clave = aggregateId: preserva el orden de los eventos de una misma transferencia.
        String key = event.getAggregateId().toString();
        ProducerRecord<String, String> record =
                new ProducerRecord<>(properties.topic(), key, event.getPayload());

        // Esquema versionado en headers (Requisito 5, criterio 4).
        Headers headers = record.headers();
        headers.add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
        headers.add(HEADER_EVENT_VERSION,
                Integer.toString(event.getEventVersion()).getBytes(StandardCharsets.UTF_8));

        // Envío síncrono: bloquea hasta el ack (o excepción) para no marcar publicado sin confirmar.
        kafkaTemplate.send(record).get();
    }
}
