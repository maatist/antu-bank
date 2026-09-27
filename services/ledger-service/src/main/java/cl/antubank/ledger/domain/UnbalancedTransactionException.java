package cl.antubank.ledger.domain;

import java.math.BigDecimal;

/**
 * Se lanza cuando se intenta construir una transacción contable cuyos asientos no
 * suman exactamente cero (Σ débitos ≠ Σ créditos).
 *
 * <p>Corresponde al criterio de aceptación 2 del Requisito 3: rechazar transacciones
 * desbalanceadas (Σ ≠ 0).
 */
public class UnbalancedTransactionException extends RuntimeException {

    public UnbalancedTransactionException(String message) {
        super(message);
    }

    /**
     * Construye la excepción a partir del desbalance detectado.
     *
     * @param netImbalance suma neta (con signo) de los asientos; distinta de cero.
     */
    public static UnbalancedTransactionException of(BigDecimal netImbalance) {
        return new UnbalancedTransactionException(
                "Transacción desbalanceada: la suma neta de los asientos debe ser cero, "
                        + "pero fue " + netImbalance.toPlainString()
                        + " (Σ débitos ≠ Σ créditos).");
    }
}
