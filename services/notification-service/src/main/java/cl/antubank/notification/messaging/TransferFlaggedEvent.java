package cl.antubank.notification.messaging;

import cl.antubank.domain.money.Currency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * DTO <em>del lado del consumer</em> para el evento {@code TransferFlagged} publicado por
 * fraud-service (tarea 7.2, Requisito 7, criterio 3).
 *
 * <p>Reproduce la forma del payload que fraud-service serializa a JSON al publicar en el topic
 * {@code fraud.events}, pero <strong>sin</strong> depender del módulo de fraude (contract-by-copy).
 * El contrato compartido es el <em>esquema JSON</em> del evento, versionado mediante los headers de
 * Kafka {@code eventType} / {@code eventVersion}.
 *
 * <p>Se anota {@link JsonIgnoreProperties}{@code (ignoreUnknown = true)} para tolerar campos
 * adicionales que versiones futuras del productor puedan incorporar sin romper el consumo.
 *
 * @param transferId      transferencia marcada como sospechosa.
 * @param sourceAccountId cuenta origen a la que se atribuye el riesgo (titular a notificar).
 * @param reason          regla que se disparó (motivo del marcado).
 * @param amountMinor     monto de la transferencia en minor units (CLP: pesos enteros).
 * @param currency        moneda del monto (CLP como principal).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferFlaggedEvent(
        UUID transferId,
        UUID sourceAccountId,
        FraudReason reason,
        long amountMinor,
        Currency currency) {

    /** Tipo de evento esperado en el header {@code eventType} (discriminador de esquema). */
    public static final String EVENT_TYPE = "TransferFlagged";

    /** Versión del esquema soportada por este consumer (header {@code eventVersion}). */
    public static final int EVENT_VERSION = 1;
}
