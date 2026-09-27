package cl.antubank.fraud.rules;

/**
 * Motivo por el cual una transferencia fue marcada como sospechosa (Requisito 7, criterio 2).
 *
 * <p>Cada valor identifica la regla que se disparó, para trazabilidad y para que los consumidores
 * del evento {@code TransferFlagged} (p. ej. notification-service o un panel de riesgo) puedan
 * enrutar o priorizar según el tipo de riesgo detectado.
 */
public enum FraudReason {

    /** El monto de una única transferencia supera el umbral de monto alto configurado. */
    HIGH_AMOUNT,

    /**
     * La cuenta origen superó el número máximo de transferencias permitidas dentro de la ventana
     * de tiempo configurada (regla de velocidad por frecuencia).
     */
    VELOCITY_COUNT,

    /**
     * La cuenta origen acumuló, dentro de la ventana de tiempo, un monto total que supera el
     * umbral acumulado configurado (regla de velocidad por monto acumulado).
     */
    VELOCITY_AMOUNT
}
