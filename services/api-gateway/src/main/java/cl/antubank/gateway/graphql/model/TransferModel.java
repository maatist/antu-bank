package cl.antubank.gateway.graphql.model;

import cl.antubank.gateway.graphql.dto.TransferDto;

/**
 * Modelo del tipo GraphQL {@code Transfer}. Refleja la transferencia de transfer-service.
 */
public record TransferModel(
        String id,
        String status,
        String sourceAccountId,
        String destinationAccountId,
        long amountMinor,
        String currency,
        String createdAt) {

    /** Construye el modelo a partir de la respuesta REST de transfer-service. */
    public static TransferModel from(TransferDto dto) {
        return new TransferModel(
                dto.id(),
                dto.status(),
                dto.sourceAccountId(),
                dto.destinationAccountId(),
                dto.amountMinor(),
                dto.currency(),
                dto.createdAt());
    }
}
