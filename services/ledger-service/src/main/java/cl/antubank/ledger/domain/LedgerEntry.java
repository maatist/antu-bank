package cl.antubank.ledger.domain;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Asiento individual (línea) de una transacción de doble entrada.
 *
 * <p>Un asiento afecta a una cuenta ({@code accountId}) con un {@link Money} positivo y una
 * naturaleza contable {@link EntryType} (débito o crédito). Nunca usa punto flotante: el monto
 * se apoya en el value object {@link Money} (BigDecimal + escala por moneda).
 *
 * <p>Value object inmutable con igualdad por valor. La restricción de balance (Σ = 0) es
 * responsabilidad de {@link LedgerTransaction}, que agrupa a varios asientos.
 */
public final class LedgerEntry {

    private final UUID accountId;
    private final EntryType type;
    private final Money amount;

    private LedgerEntry(UUID accountId, EntryType type, Money amount) {
        this.accountId = Objects.requireNonNull(accountId, "accountId no puede ser null");
        this.type = Objects.requireNonNull(type, "type no puede ser null");
        this.amount = Objects.requireNonNull(amount, "amount no puede ser null");
        if (amount.isNegative()) {
            throw new IllegalArgumentException(
                    "El monto de un asiento debe ser no negativo; el signo lo determina el tipo (débito/crédito).");
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("El monto de un asiento no puede ser cero.");
        }
    }

    /**
     * Crea un asiento de débito sobre una cuenta.
     */
    public static LedgerEntry debit(UUID accountId, Money amount) {
        return new LedgerEntry(accountId, EntryType.DEBIT, amount);
    }

    /**
     * Crea un asiento de crédito sobre una cuenta.
     */
    public static LedgerEntry credit(UUID accountId, Money amount) {
        return new LedgerEntry(accountId, EntryType.CREDIT, amount);
    }

    /**
     * Crea un asiento con tipo explícito.
     */
    public static LedgerEntry of(UUID accountId, EntryType type, Money amount) {
        return new LedgerEntry(accountId, type, amount);
    }

    /**
     * Aporte del asiento al balance de la transacción, con signo según el tipo:
     * positivo para débito, negativo para crédito.
     *
     * @return monto con signo (en unidades mayores de la moneda).
     */
    public BigDecimal signedAmount() {
        return amount.amount().multiply(BigDecimal.valueOf(type.sign()));
    }

    public UUID accountId() {
        return accountId;
    }

    public EntryType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    public Currency currency() {
        return amount.currency();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerEntry other)) {
            return false;
        }
        return accountId.equals(other.accountId)
                && type == other.type
                && amount.equals(other.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, type, amount);
    }

    @Override
    public String toString() {
        return type + " " + amount + " @ " + accountId;
    }
}
