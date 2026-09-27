package cl.antubank.ledger.domain;

import cl.antubank.domain.money.Currency;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Transacción contable de doble entrada: agregado que agrupa N {@link LedgerEntry}
 * (débitos y créditos) e impone el invariante fundamental del libro mayor.
 *
 * <p>Invariante Σ = 0 (ver requirements.md, Requisito 3):
 * <ul>
 *   <li>Criterio 1: los asientos de débito y crédito deben sumar exactamente cero.</li>
 *   <li>Criterio 2: una transacción desbalanceada (Σ ≠ 0) es rechazada al construirse.</li>
 * </ul>
 *
 * <p>Reglas adicionales de dominio:
 * <ul>
 *   <li>Debe tener al menos dos asientos (una doble entrada mínima: un débito y un crédito).</li>
 *   <li>Todos los asientos deben compartir la misma moneda; no se mezclan monedas dentro de
 *       una transacción (la conversión, si aplica, ocurre fuera del ledger).</li>
 * </ul>
 *
 * <p>Objeto inmutable: la lista de asientos se copia defensivamente y se expone sin modificación.
 */
public final class LedgerTransaction {

    private final UUID id;
    private final String reference;
    private final Currency currency;
    private final List<LedgerEntry> entries;

    private LedgerTransaction(UUID id, String reference, List<LedgerEntry> entries) {
        this.id = Objects.requireNonNull(id, "id no puede ser null");
        this.reference = reference;

        Objects.requireNonNull(entries, "entries no puede ser null");
        if (entries.size() < 2) {
            throw new IllegalArgumentException(
                    "Una transacción de doble entrada requiere al menos dos asientos.");
        }
        this.entries = List.copyOf(entries);

        this.currency = requireSingleCurrency(this.entries);
        requireBalanced(this.entries);
    }

    /**
     * Crea una transacción con un identificador nuevo.
     *
     * @param reference referencia de negocio opcional (ej. id de transferencia origen).
     * @param entries   asientos de la transacción (débitos y créditos).
     * @throws UnbalancedTransactionException si Σ ≠ 0.
     */
    public static LedgerTransaction of(String reference, List<LedgerEntry> entries) {
        return new LedgerTransaction(UUID.randomUUID(), reference, entries);
    }

    /**
     * Crea una transacción con un identificador explícito (útil al reconstruir desde persistencia).
     *
     * @throws UnbalancedTransactionException si Σ ≠ 0.
     */
    public static LedgerTransaction of(UUID id, String reference, List<LedgerEntry> entries) {
        return new LedgerTransaction(id, reference, entries);
    }

    private static Currency requireSingleCurrency(List<LedgerEntry> entries) {
        Currency currency = entries.get(0).currency();
        for (LedgerEntry entry : entries) {
            if (entry.currency() != currency) {
                throw new IllegalArgumentException(
                        "Todos los asientos de una transacción deben usar la misma moneda; "
                                + "se encontró " + entry.currency() + " y " + currency + ".");
            }
        }
        return currency;
    }

    /**
     * Verifica el invariante Σ = 0 sumando los aportes con signo de cada asiento
     * (débito positivo, crédito negativo).
     */
    private static void requireBalanced(List<LedgerEntry> entries) {
        BigDecimal net = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            net = net.add(entry.signedAmount());
        }
        if (net.signum() != 0) {
            throw UnbalancedTransactionException.of(net);
        }
    }

    public UUID id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public Currency currency() {
        return currency;
    }

    /**
     * @return lista inmutable de asientos de la transacción.
     */
    public List<LedgerEntry> entries() {
        return entries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerTransaction other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LedgerTransaction{id=" + id + ", reference=" + reference
                + ", currency=" + currency + ", entries=" + entries.size() + "}";
    }
}
