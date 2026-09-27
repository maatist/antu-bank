package cl.antubank.transfer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que persiste una {@code Idempotency-Key} junto al resultado de la transferencia
 * que produjo (ver requirements.md, Requisito 4, criterio 1).
 *
 * <p>La columna {@code idem_key} tiene una <b>restricción única</b>: al recibir otra solicitud
 * con la misma clave, el servicio recupera esta fila y retorna el mismo resultado sin aplicar un
 * nuevo movimiento (criterio 2). Ante dos solicitudes concurrentes con la misma clave, la unicidad
 * a nivel de base de datos garantiza que solo una prospere y, por tanto, que solo se aplique un
 * movimiento (criterio 3). La lógica que consume esta garantía se agrega en la tarea 4.2.
 */
@Entity
@Table(
        name = "idempotency_key",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_idempotency_key",
                columnNames = "idem_key"))
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Valor de la clave de idempotencia provista por el cliente (header Idempotency-Key). */
    @Column(name = "idem_key", nullable = false, updatable = false, length = 200)
    private String idempotencyKey;

    /** Transferencia (resultado) asociada a esta clave. */
    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
        // Requerido por JPA.
    }

    public IdempotencyKeyEntity(UUID id, String idempotencyKey, UUID transferId) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.transferId = transferId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
