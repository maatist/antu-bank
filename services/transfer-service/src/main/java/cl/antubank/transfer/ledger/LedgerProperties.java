package cl.antubank.transfer.ledger;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración del cliente hacia ledger-service.
 *
 * <p>La URL base es configurable (propiedad {@code ledger.base-url}, con override por variable de
 * entorno {@code LEDGER_BASE_URL}) y por defecto apunta al ledger-service local (puerto 8083).
 * Esta invocación directa es temporal para lograr un flujo end-to-end temprano (tarea 4.3);
 * a partir de la tarea 5 el asiento se genera vía eventos Kafka (Outbox).
 *
 * @param baseUrl URL base del ledger-service (ej. {@code http://localhost:8083}).
 */
@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(String baseUrl) {
}
