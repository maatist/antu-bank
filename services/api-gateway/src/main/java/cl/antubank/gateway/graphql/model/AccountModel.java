package cl.antubank.gateway.graphql.model;

import cl.antubank.gateway.graphql.dto.AccountDto;

/**
 * Modelo del tipo GraphQL {@code Account}. Refleja la cuenta de account-service y transporta el
 * {@code bearerToken} entrante para que el campo anidado {@code balance} pueda resolverse contra
 * ledger-service propagando el token (resuelto por {@code @SchemaMapping} en el controlador).
 *
 * <p>El token no se expone en el schema GraphQL: es un detalle interno del modelo usado solo por
 * el resolver de {@code balance}.
 */
public record AccountModel(
        String id,
        String holderRut,
        String holderName,
        String accountType,
        String bank,
        String bankName,
        String currency,
        String status,
        String bearerToken) {

    /** Construye el modelo a partir de la respuesta REST de account-service y el token entrante. */
    public static AccountModel from(AccountDto dto, String bearerToken) {
        return new AccountModel(
                dto.id(),
                dto.holderRut(),
                dto.holderName(),
                dto.accountType(),
                dto.bank(),
                dto.bankName(),
                dto.currency(),
                dto.status(),
                bearerToken);
    }
}
