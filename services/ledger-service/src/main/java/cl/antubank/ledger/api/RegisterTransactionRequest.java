package cl.antubank.ledger.api;

import cl.antubank.domain.money.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Solicitud para registrar una transacción contable de doble entrada.
 *
 * <p>Debe contener al menos dos asientos (una doble entrada mínima) cuya suma neta sea cero,
 * todos en la misma moneda. El invariante Σ = 0 se valida en el dominio al construir la
 * transacción (ver requirements.md, Requisito 3, criterios 1 y 2); una transacción
 * desbalanceada se rechaza con un error {@code ProblemDetail}.
 *
 * @param reference referencia de negocio opcional (ej. id de la transferencia que la originó).
 * @param currency  moneda de todos los asientos de la transacción.
 * @param entries   asientos de débito y crédito de la transacción.
 */
@Schema(description = "Datos para registrar una transacción contable de doble entrada")
public record RegisterTransactionRequest(

        @Schema(description = "Referencia de negocio opcional", example = "transfer-8ac1")
        @Size(max = 100, message = "{field.reference.size}")
        String reference,

        @Schema(description = "Moneda de la transacción", example = "CLP")
        @NotNull(message = "{field.currency.notnull}")
        Currency currency,

        @Schema(description = "Asientos de la transacción (mínimo dos, balanceados)")
        @NotEmpty(message = "{field.entries.notempty}")
        @Size(min = 2, message = "{field.entries.min}")
        @Valid
        List<EntryRequest> entries
) {
}
