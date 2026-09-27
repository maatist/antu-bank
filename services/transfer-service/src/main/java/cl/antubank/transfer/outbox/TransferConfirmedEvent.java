package cl.antubank.transfer.outbox;

import cl.antubank.domain.money.Currency;
import java.util.UUID;

/**
 * Evento de dominio que representa la <strong>confirmación de una transferencia</strong>
 * (ver requirements.md, Requisito 5).
 *
 * <p>Este record es el <em>payload</em> del evento que se serializa a JSON y se persiste en la
 * tabla {@code outbox} dentro de la misma transacción de negocio (Requisito 5, criterio 1). El
 * relay (tarea 5.2) lo publicará a Kafka y ledger-service (tarea 5.3) lo consumirá para generar el
 * asiento de doble entrada.
 *
 * <p>El esquema del evento está <strong>versionado</strong> (Requisito 5, criterio 4): el tipo y la
 * versión no viajan dentro del payload sino en las columnas {@code event_type} / {@code event_version}
 * de la fila de outbox, cuyas constantes se definen aquí ({@link #EVENT_TYPE} / {@link #EVENT_VERSION})
 * para mantener acoplado el contrato con su serialización.
 *
 * <p>El monto se expresa en <em>minor units</em> ({@code amountMinor}); para CLP —moneda principal—
 * son pesos enteros, evitando errores de precisión de punto flotante (Requisito 4, criterio 4).
 *
 * @param transferId           identificador de la transferencia confirmada (agregado de origen).
 * @param sourceAccountId      cuenta origen (débito).
 * @param destinationAccountId cuenta destino (crédito).
 * @param amountMinor          monto en minor units (CLP: pesos enteros).
 * @param currency             moneda del monto (CLP como principal).
 */
public record TransferConfirmedEvent(
        UUID transferId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountMinor,
        Currency currency) {

    /** Tipo de evento, usado como discriminador versionado del esquema (Requisito 5, criterio 4). */
    public static final String EVENT_TYPE = "TransferConfirmed";

    /** Versión del esquema de este evento. Incrementar ante cambios incompatibles del payload. */
    public static final int EVENT_VERSION = 1;

    /** Tipo de agregado que origina el evento; sirve de contexto para el relay y los consumidores. */
    public static final String AGGREGATE_TYPE = "Transfer";
}
