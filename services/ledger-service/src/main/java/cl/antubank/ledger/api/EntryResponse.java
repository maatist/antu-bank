package cl.antubank.ledger.api;

import cl.antubank.domain.money.Currency;
import cl.antubank.ledger.domain.EntryType;
import cl.antubank.ledger.domain.LedgerEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Representación de un asiento en las respuestas de la API.
 *
 * @param accountId   cuenta afectada.
 * @param type        naturaleza contable (débito/crédito).
 * @param amountMinor monto en minor units (siempre positivo; el signo lo da el {@code type}).
 * @param currency    moneda del asiento.
 */
@Schema(description = "Asiento de una transacción contable")
public record EntryResponse(

        @Schema(description = "Identificador de la cuenta afectada")
        UUID accountId,

        @Schema(description = "Naturaleza contable del asiento")
        EntryType type,

        @Schema(description = "Monto en minor units")
        long amountMinor,

        @Schema(description = "Moneda del asiento")
        Currency currency
) {

    /**
     * Construye la respuesta a partir de un asiento de dominio.
     */
    public static EntryResponse from(LedgerEntry entry) {
        return new EntryResponse(
                entry.accountId(),
                entry.type(),
                entry.amount().toMinorUnits(),
                entry.currency());
    }
}
