package cl.antubank.ledger.api;

import cl.antubank.domain.money.Currency;
import cl.antubank.ledger.domain.LedgerEntry;
import cl.antubank.ledger.domain.LedgerTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Representación de una transacción contable registrada.
 *
 * @param id        identificador de la transacción creada.
 * @param reference referencia de negocio (puede ser {@code null}).
 * @param currency  moneda de la transacción.
 * @param entries   asientos que componen la transacción.
 */
@Schema(description = "Transacción contable de doble entrada")
public record TransactionResponse(

        @Schema(description = "Identificador de la transacción")
        UUID id,

        @Schema(description = "Referencia de negocio", example = "transfer-8ac1")
        String reference,

        @Schema(description = "Moneda de la transacción")
        Currency currency,

        @Schema(description = "Asientos de la transacción")
        List<EntryResponse> entries
) {

    /**
     * Construye la respuesta a partir del agregado de dominio.
     */
    public static TransactionResponse from(LedgerTransaction transaction) {
        List<EntryResponse> entries = transaction.entries().stream()
                .map(EntryResponse::from)
                .toList();
        return new TransactionResponse(
                transaction.id(),
                transaction.reference(),
                transaction.currency(),
                entries);
    }
}
