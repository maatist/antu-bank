package cl.antubank.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Proyección de una transferencia tal como la retorna transfer-service (TransferResponse) para el
 * BFF. El estado y la moneda llegan como cadenas JSON. Se mapea al tipo GraphQL {@code Transfer}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferDto(
        String id,
        String status,
        String sourceAccountId,
        String destinationAccountId,
        long amountMinor,
        String currency,
        String createdAt) {
}
