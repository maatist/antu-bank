package cl.antubank.fraud.messaging;

import cl.antubank.domain.money.Currency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * DTO <em>del lado del consumer</em> para el evento {@code TransferConfirmed} publicado por
 * transfer-service (tarea 7.1, Requisito 7, criterio 1).
 *
 * <p>Reproduce la forma del payload que transfer-service serializa a JSON en su outbox, pero
 * <strong>sin</strong> depender del módulo de transfer-service: cada servicio es autónomo
 * (contract-by-copy). El contrato compartido es el <em>esquema JSON</em> del evento, versionado
 * mediante los headers de Kafka {@code eventType} / {@code eventVersion}.
 *
 * <p>Se anota {@link JsonIgnoreProperties}{@code (ignoreUnknown = true)} para tolerar campos
 * adicionales que versiones futuras del productor puedan incorporar sin romper el consumo.
 *
 * <p>El monto viaja en <em>minor units</em> ({@code amountMinor}); para CLP —moneda principal— son
 * pesos enteros, preservando la exactitud (Requisito 1, criterio 1).
 *
 * @param transferId           id de la transferencia confirmada.
 * @param sourceAccountId      cuenta origen (sujeta a las reglas de fraude por cuenta).
 * @param destinationAccountId cuenta destino.
 * @param amountMinor          monto en minor units (CLP: pesos enteros).
 * @param currency             moneda del monto (CLP como principal).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferConfirmedEvent(
        UUID transferId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountMinor,
        Currency currency) {

    /** Tipo de evento esperado en el header {@code eventType} (discriminador de esquema). */
    public static final String EVENT_TYPE = "TransferConfirmed";

    /** Versión del esquema soportada por este consumer (header {@code eventVersion}). */
    public static final int EVENT_VERSION = 1;
}
