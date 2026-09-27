package cl.antubank.transfer.persistence;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa una transferencia entre dos cuentas.
 *
 * <p>El monto se persiste como {@code amount_minor}: el monto en minor units de la moneda,
 * expresado como entero (para CLP, pesos enteros). Guardar minor units enteros evita cualquier
 * error de precisión de punto flotante y se reconstituye como {@link Money} mediante
 * {@link #getAmount()} (ver requirements.md, Requisito 1 y Requisito 4, criterio 4: CLP).
 *
 * <p>La idempotencia se modela en {@link IdempotencyKeyEntity}, que referencia a esta
 * transferencia (ver Requisito 4, criterio 1: persistir la clave junto al resultado). El endpoint
 * y la lógica de idempotencia/validación de fondos llegan en las tareas 4.2–4.4.
 */
@Entity
@Table(name = "transfer")
public class TransferEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cuenta origen (identificador de account-service). */
    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    /** Cuenta destino (identificador de account-service). */
    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    /** Monto en minor units de la moneda (para CLP, pesos enteros). */
    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransferEntity() {
        // Requerido por JPA.
    }

    public TransferEntity(UUID id, UUID sourceAccountId, UUID destinationAccountId,
                          Money amount, TransferStatus status) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amountMinor = amount.toMinorUnits();
        this.currency = amount.currency();
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * @return el monto reconstituido como value object {@link Money} desde sus minor units.
     */
    public Money getAmount() {
        return Money.ofMinor(amountMinor, currency);
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Currency getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
