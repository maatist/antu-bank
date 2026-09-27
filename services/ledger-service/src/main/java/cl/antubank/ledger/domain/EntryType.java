package cl.antubank.ledger.domain;

/**
 * Naturaleza contable de un asiento dentro de una transacción de doble entrada.
 *
 * <p>En contabilidad de doble entrada, cada transacción se compone de asientos de
 * débito y crédito cuya suma neta debe ser exactamente cero
 * (ver requirements.md, Requisito 3, criterio 1).
 *
 * <p>Convención de signo usada por el dominio para verificar el balance:
 * <ul>
 *   <li>{@link #DEBIT} aporta con signo positivo (+).</li>
 *   <li>{@link #CREDIT} aporta con signo negativo (−).</li>
 * </ul>
 * De modo que {@code Σ débitos − Σ créditos = 0} equivale a {@code Σ (aportes con signo) = 0}.
 */
public enum EntryType {

    /** Débito: aporta con signo positivo al balance de la transacción. */
    DEBIT(1),

    /** Crédito: aporta con signo negativo al balance de la transacción. */
    CREDIT(-1);

    private final int sign;

    EntryType(int sign) {
        this.sign = sign;
    }

    /**
     * @return {@code +1} para débito, {@code -1} para crédito.
     */
    public int sign() {
        return sign;
    }
}
