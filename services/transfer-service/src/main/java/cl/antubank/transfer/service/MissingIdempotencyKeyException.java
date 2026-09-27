package cl.antubank.transfer.service;

/**
 * Se lanza cuando una solicitud de transferencia no incluye (o incluye en blanco) el header
 * {@code Idempotency-Key}, que es obligatorio para garantizar la idempotencia
 * (ver requirements.md, Requisito 4, criterio 1).
 */
public class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException() {
        super("Falta el header Idempotency-Key");
    }
}
