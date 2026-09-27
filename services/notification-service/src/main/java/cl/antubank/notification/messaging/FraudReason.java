package cl.antubank.notification.messaging;

/**
 * Motivo por el cual una transferencia fue marcada como sospechosa por fraud-service (Requisito 7,
 * criterio 2), transportado dentro del evento {@code TransferFlagged}.
 *
 * <p>Es una copia <em>del lado del consumer</em> del enum homónimo de fraud-service (contract-by-copy):
 * notification-service no depende del módulo de fraude. El valor determina la plantilla de mensaje
 * localizada que describe el riesgo al usuario ({@code notification.reason.*}).
 */
public enum FraudReason {

    /** El monto de una única transferencia supera el umbral de monto alto configurado. */
    HIGH_AMOUNT,

    /** La cuenta superó el número máximo de transferencias permitidas dentro de la ventana. */
    VELOCITY_COUNT,

    /** La cuenta acumuló, dentro de la ventana, un monto total que supera el umbral configurado. */
    VELOCITY_AMOUNT
}
