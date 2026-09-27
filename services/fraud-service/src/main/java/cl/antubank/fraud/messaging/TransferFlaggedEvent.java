package cl.antubank.fraud.messaging;

import cl.antubank.domain.money.Currency;
import cl.antubank.fraud.rules.FraudReason;
import java.util.UUID;

/**
 * Evento de dominio que representa que una transferencia fue <strong>marcada como sospechosa</strong>
 * por el motor de reglas de fraude (tarea 7.1, Requisito 7, criterio 2).
 *
 * <p>Este record es el <em>payload</em> que se serializa a JSON y se publica en el topic de eventos
 * de fraude. El esquema está <strong>versionado</strong>: el tipo y la versión no viajan dentro del
 * payload sino en los headers de Kafka {@code eventType} / {@code eventVersion} (mismas constantes
 * y convención que el outbox relay de transfer-service), de modo que los consumidores puedan enrutar
 * y deserializar sin ambigüedad.
 *
 * <p>El monto se expresa en <em>minor units</em> ({@code amountMinor}); para CLP —moneda principal—
 * son pesos enteros, evitando errores de precisión de punto flotante.
 *
 * @param transferId      transferencia marcada (referencia para consumidores aguas abajo).
 * @param sourceAccountId cuenta origen a la que se atribuye el riesgo.
 * @param reason          regla que se disparó (motivo del marcado).
 * @param amountMinor     monto de la transferencia en minor units (CLP: pesos enteros).
 * @param currency        moneda del monto (CLP como principal).
 */
public record TransferFlaggedEvent(
        UUID transferId,
        UUID sourceAccountId,
        FraudReason reason,
        long amountMinor,
        Currency currency) {

    /** Tipo de evento, discriminador versionado del esquema (header {@code eventType}). */
    public static final String EVENT_TYPE = "TransferFlagged";

    /** Versión del esquema de este evento. Incrementar ante cambios incompatibles del payload. */
    public static final int EVENT_VERSION = 1;

    /** Tipo de agregado que origina el evento; contexto para consumidores. */
    public static final String AGGREGATE_TYPE = "Transfer";
}
