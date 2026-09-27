package cl.antubank.ledger.api;

import cl.antubank.ledger.domain.EntryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Un asiento (línea) dentro de una transacción contable a registrar.
 *
 * <p>El monto se expresa en <em>minor units</em> (la menor unidad de la moneda): para CLP son
 * pesos enteros; para USD/UF son centésimas. El signo lo determina el {@link EntryType}
 * (débito/crédito), por lo que el monto siempre es un entero positivo (ver requirements.md,
 * Requisito 1, criterio 1 y Requisito 3, criterio 1).
 *
 * @param accountId   cuenta afectada por el asiento.
 * @param type        naturaleza contable del asiento (débito o crédito).
 * @param amountMinor monto en minor units; debe ser un entero positivo.
 */
@Schema(description = "Asiento de débito o crédito dentro de una transacción")
public record EntryRequest(

        @Schema(description = "Identificador de la cuenta afectada",
                example = "3f1e5b2a-1c4d-4e9a-9b7c-2a6f8d0e1234")
        @NotNull(message = "{field.entry.accountId.notnull}")
        UUID accountId,

        @Schema(description = "Naturaleza contable del asiento", example = "DEBIT")
        @NotNull(message = "{field.entry.type.notnull}")
        EntryType type,

        @Schema(description = "Monto en minor units (CLP: pesos enteros; USD/UF: centésimas)",
                example = "30000")
        @NotNull(message = "{field.entry.amount.notnull}")
        @Positive(message = "{field.entry.amount.positive}")
        Long amountMinor
) {
}
