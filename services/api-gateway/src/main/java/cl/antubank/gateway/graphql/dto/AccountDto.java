package cl.antubank.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Proyección de la cuenta tal como la retorna account-service (AccountResponse) para el BFF.
 *
 * <p>Los enums del servicio (tipo de cuenta, banco, moneda, estado) llegan como cadenas JSON, por
 * lo que aquí se modelan como {@code String}. Se ignoran campos desconocidos para tolerar la
 * evolución del contrato REST sin romper el BFF.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(
        String id,
        String holderRut,
        String holderName,
        String accountType,
        String bank,
        String bankName,
        String currency,
        String status) {
}
