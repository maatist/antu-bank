package cl.antubank.transfer.service;

import cl.antubank.transfer.persistence.TransferEntity;

/**
 * Resultado de procesar una solicitud de transferencia idempotente.
 *
 * <p>Distingue si la transferencia se acaba de crear (primera vez que se ve la
 * {@code Idempotency-Key}) o si se está reproduciendo un resultado ya almacenado ante un
 * reintento con la misma clave. La capa web usa esta distinción para responder {@code 201 Created}
 * en el primer caso y {@code 200 OK} en la repetición, sin aplicar un nuevo movimiento
 * (ver requirements.md, Requisito 4, criterios 1 y 2; design.md, sección 6.1).
 *
 * @param transfer la transferencia (resultado) asociada a la clave.
 * @param replayed {@code true} si es la reproducción de un resultado previo; {@code false} si
 *                 la transferencia se creó en esta solicitud.
 */
public record TransferResult(TransferEntity transfer, boolean replayed) {

    static TransferResult created(TransferEntity transfer) {
        return new TransferResult(transfer, false);
    }

    static TransferResult replayed(TransferEntity transfer) {
        return new TransferResult(transfer, true);
    }
}
