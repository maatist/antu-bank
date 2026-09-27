package cl.antubank.transfer.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link OutboxEventEntity}.
 *
 * <p>Además del CRUD, expone la consulta de eventos pendientes de publicar (usada por el relay de
 * la tarea 5.2) ordenados por momento de ocurrencia para preservar el orden de los eventos.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Recupera los eventos aún no publicados a Kafka, ordenados por {@code occurredAt} ascendente.
     */
    List<OutboxEventEntity> findByPublishedFalseOrderByOccurredAtAsc();

    /** Recupera todos los eventos de un agregado dado, en orden de ocurrencia. */
    List<OutboxEventEntity> findByAggregateId(UUID aggregateId, Sort sort);
}
