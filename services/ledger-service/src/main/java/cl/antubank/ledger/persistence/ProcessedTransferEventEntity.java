package cl.antubank.ledger.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Marca de <strong>evento de transferencia ya procesado</strong>, base de la idempotencia del
 * consumo de eventos (tarea 5.3, Requisito 5, criterios 3 y 5).
 *
 * <p>Su clave primaria es el {@code transferId} del evento {@code TransferConfirmed}. El consumer
 * inserta esta marca en la misma transacción en que genera el asiento; si el mismo evento se
 * reentrega (garantía "al menos una vez" de Kafka), la clave primaria rechaza la segunda inserción
 * y el asiento no se duplica. La marca y el asiento son atómicos: existen ambos o ninguno.
 */
@Entity
@Table(name = "processed_transfer_event")
public class ProcessedTransferEventEntity {

    @Id
    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedTransferEventEntity() {
        // Requerido por JPA.
    }

    public ProcessedTransferEventEntity(UUID transferId) {
        this.transferId = transferId;
    }

    @PrePersist
    void onCreate() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }

    public UUID getTransferId() {
        return transferId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
