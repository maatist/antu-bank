package cl.antubank.account.api;

import cl.antubank.account.validation.ValidRut;
import cl.antubank.domain.account.AccountType;
import cl.antubank.domain.account.ChileanBank;
import cl.antubank.domain.money.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud de creación de una cuenta.
 */
@Schema(description = "Datos para crear una cuenta bancaria")
public record CreateAccountRequest(

        @Schema(description = "RUT del titular", example = "12.345.678-5")
        @NotBlank(message = "{field.rut.notblank}")
        @ValidRut
        String holderRut,

        @Schema(description = "Nombre del titular", example = "María González")
        @NotBlank(message = "{field.holderName.notblank}")
        String holderName,

        @Schema(description = "Tipo de cuenta", example = "CORRIENTE")
        @NotNull(message = "{field.accountType.notnull}")
        AccountType accountType,

        @Schema(description = "Banco de la plaza chilena", example = "BANCO_ESTADO")
        @NotNull(message = "{field.bank.notnull}")
        ChileanBank bank,

        @Schema(description = "Moneda de la cuenta", example = "CLP")
        @NotNull(message = "{field.currency.notnull}")
        Currency currency
) {
}
