package cl.antubank.gateway.graphql;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URIs de los servicios internos que agrega el BFF GraphQL (tarea 9.3, Requisito 9, criterio 5).
 *
 * <p>Se enlazan desde {@code antubank.gateway.bff.*} en {@code application.yml} y reutilizan las
 * mismas variables de entorno que las rutas del gateway ({@code ACCOUNT_SERVICE_URI}, etc.), de
 * modo que el BFF llama directamente por REST a account/ledger/transfer.
 *
 * @param accountServiceUri  base URL de account-service (ej. http://localhost:8082).
 * @param ledgerServiceUri   base URL de ledger-service (ej. http://localhost:8083).
 * @param transferServiceUri base URL de transfer-service (ej. http://localhost:8084).
 */
@ConfigurationProperties(prefix = "antubank.gateway.bff")
public record BffProperties(
        String accountServiceUri,
        String ledgerServiceUri,
        String transferServiceUri) {
}
