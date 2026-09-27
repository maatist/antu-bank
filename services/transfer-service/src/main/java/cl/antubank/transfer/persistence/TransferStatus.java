package cl.antubank.transfer.persistence;

/**
 * Estado del ciclo de vida de una transferencia.
 *
 * <p>En la tarea 4 (flujo síncrono) una transferencia nace como {@link #PENDING} y se resuelve
 * a {@link #CONFIRMED} o {@link #REJECTED}. Los estados intermedios de la saga (reserva,
 * asiento, compensación) se incorporan a partir de la tarea 6.
 */
public enum TransferStatus {

    /** Transferencia recibida y validada, aún sin confirmar. */
    PENDING,

    /** Transferencia aplicada exitosamente. */
    CONFIRMED,

    /** Transferencia rechazada (por ejemplo, por fondos insuficientes). */
    REJECTED
}
