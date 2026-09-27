package cl.antubank.transfer.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidad JPA que materializa el <strong>Outbox pattern</strong>: cada fila es un evento de dominio
 * pendiente de publicar a Kafka (ver requirements.md, Requisito 5; design.md, sección 6.2 y
 * ADR-005).
 *
 * <p>La clave del patrón es que esta fila se inserta <strong>dentro de la misma transacción</strong>
 * que la escritura de negocio (la {@code TransferEntity} y su {@code IdempotencyKeyEntity}). Así, o
 * bien se confirman todas juntas o ninguna: nunca se publica un evento sin que la transferencia se
 * haya persistido, ni se persiste la transferencia sin dejar registrado el evento a publicar
 * (Requisito 5, criterio 1: se resuelve el dual-write DB↔Kafka).
 *
 * <p>El esquema del evento está <strong>versionado</strong> mediante {@link #eventType} +
 * {@link #eventVersion} (Requisito 5, criterio 4), lo que permite evolucionar el {@link #payload}
 * (JSON) manteniendo compatibilidad con los consumidores.
 *
 * <p>El {@link #payload} se persiste como {@code JSONB} en PostgreSQL. El relay (tarea 5.2) leerá
 * las filas con {@link #published} en {@code false}, las publicará a Kafka y las marcará como
 * publicadas fijando {@link #publishedAt}.
 */
@Entity
@Table(name = "outbox")
public class OutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tipo de agregado que originó el evento (ej. {@code "Transfer"}). */
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
    private String aggregateType;

    /** Identificador del agregado (ej. el id de la transferencia); clave de partición aguas abajo. */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** Tipo de evento (ej. {@code "TransferConfirmed"}). */
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    /** Versión del esquema del evento (Requisito 5, criterio 4). */
    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    /** Payload del evento serializado como JSON (columna {@code JSONB}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    /** Momento de ocurrencia del evento de negocio. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Indica si el evento ya fue publicado a Kafka por el relay (tarea 5.2). */
    @Column(name = "published", nullable = false)
    private boolean published;

    /** Momento de publicación a Kafka; nulo mientras el evento esté pendiente. */
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {
        // Requerido por JPA.
    }

    public OutboxEventEntity(UUID id, String aggregateType, UUID aggregateId,
                             String eventType, int eventVersion, String payload) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.published = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    /**
     * Marca el evento como publicado a Kafka, fijando el momento de publicación. Lo usará el relay
     * de la tarea 5.2 tras confirmar la publicación.
     *
     * @param publishedAt momento de publicación (no nulo).
     */
    public void markPublished(Instant publishedAt) {
        this.published = true;
        this.publishedAt = publishedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
