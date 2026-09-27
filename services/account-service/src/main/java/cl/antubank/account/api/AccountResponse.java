package cl.antubank.account.api;

import cl.antubank.account.persistence.AccountEntity;
import cl.antubank.account.persistence.AccountStatus;
import cl.antubank.domain.account.AccountType;
import cl.antubank.domain.account.ChileanBank;
import cl.antubank.domain.money.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación de una cuenta en las respuestas de la API.
 */
@Schema(description = "Cuenta bancaria")
public record AccountResponse(

        @Schema(description = "Identificador de la cuenta")
        UUID id,

        @Schema(description = "RUT del titular en formato chileno", example = "12.345.678-5")
        String holderRut,

        @Schema(description = "Nombre del titular")
        String holderName,

        @Schema(description = "Tipo de cuenta")
        AccountType accountType,

        @Schema(description = "Banco")
        ChileanBank bank,

        @Schema(description = "Nombre comercial del banco")
        String bankName,

        @Schema(description = "Moneda")
        Currency currency,

        @Schema(description = "Estado de la cuenta")
        AccountStatus status,

        @Schema(description = "Fecha de creación")
        Instant createdAt
) {

    /**
     * Construye la respuesta a partir de la entidad persistida.
     * El RUT se formatea a la representación chilena para presentación.
     */
    public static AccountResponse from(AccountEntity entity) {
        return new AccountResponse(
                entity.getId(),
                entity.getHolderRut().format(),
                entity.getHolderName(),
                entity.getAccountType(),
                entity.getBank(),
                entity.getBank().displayName(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
