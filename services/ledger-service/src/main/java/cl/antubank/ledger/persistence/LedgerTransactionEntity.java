package cl.antubank.ledger.persistence;

import cl.antubank.domain.money.Currency;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA que representa una transacción contable de doble entrada persistida.
 *
 * <p>Agrupa a sus {@link LedgerEntryEntity} (asientos de débito/crédito). El invariante
 * Σ = 0 se valida en el dominio ({@code LedgerTransaction}) y se refuerza en la base de
 * datos mediante un trigger (ver migración {@code V2}).
 *
 * <p>Los asientos son inmutables (sin update/delete): a nivel de aplicación mediante columnas
 * {@code updatable=false} y ausencia de rutas de modificación/borrado, y a nivel de base de datos
 * mediante triggers que rechazan UPDATE/DELETE (ver migración {@code V3}).
 */
@Entity
@Table(name = "ledger_transaction")
public class LedgerTransactionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Referencia de negocio opcional (ej. id de la transferencia que originó el asiento). */
    @Column(name = "reference", length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "transaction",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<LedgerEntryEntity> entries = new ArrayList<>();

    protected LedgerTransactionEntity() {
        // Requerido por JPA.
    }

    public LedgerTransactionEntity(UUID id, String reference, Currency currency) {
        this.id = id;
        this.reference = reference;
        this.currency = currency;
    }

    /**
     * Agrega un asiento a la transacción, estableciendo la relación bidireccional.
     */
    public void addEntry(LedgerEntryEntity entry) {
        entry.setTransaction(this);
        entries.add(entry);
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

    public String getReference() {
        return reference;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<LedgerEntryEntity> getEntries() {
        return entries;
    }
}
