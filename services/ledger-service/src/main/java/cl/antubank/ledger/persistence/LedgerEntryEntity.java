package cl.antubank.ledger.persistence;

import cl.antubank.domain.money.Currency;
import cl.antubank.ledger.domain.EntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entidad JPA que representa un asiento individual (débito o crédito) de una transacción.
 *
 * <p>El monto se persiste como {@code amount_minor}: el monto en minor units de la moneda,
 * expresado como entero con signo según el tipo de asiento (débito positivo, crédito negativo).
 * Guardar minor units enteros con signo permite que la base de datos verifique el invariante
 * Σ = 0 con aritmética exacta (sin punto flotante).
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransactionEntity transaction;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    /**
     * Monto en minor units con signo (débito &gt; 0, crédito &lt; 0). Es este valor el que
     * la base de datos suma por transacción para verificar Σ = 0.
     */
    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    protected LedgerEntryEntity() {
        // Requerido por JPA.
    }

    public LedgerEntryEntity(UUID id, UUID accountId, EntryType entryType,
                             Currency currency, long amountMinor) {
        this.id = id;
        this.accountId = accountId;
        this.entryType = entryType;
        this.currency = currency;
        this.amountMinor = amountMinor;
    }

    void setTransaction(LedgerTransactionEntity transaction) {
        this.transaction = transaction;
    }

    public UUID getId() {
        return id;
    }

    public LedgerTransactionEntity getTransaction() {
        return transaction;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public long getAmountMinor() {
        return amountMinor;
    }
}
