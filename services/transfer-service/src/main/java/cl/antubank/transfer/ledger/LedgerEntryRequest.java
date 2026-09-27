package cl.antubank.transfer.ledger;

import java.util.UUID;

/**
 * Asiento (línea) de una transacción a registrar en el ledger-service.
 *
 * <p>Reproduce el contrato del ledger ({@code EntryRequest}): el monto viaja en minor units (para
 * CLP, pesos enteros) y siempre positivo; el signo lo determina el {@link LedgerEntryType}.
 *
 * @param accountId   cuenta afectada.
 * @param type        naturaleza contable (débito/crédito).
 * @param amountMinor monto en minor units, entero positivo.
 */
public record LedgerEntryRequest(UUID accountId, LedgerEntryType type, long amountMinor) {

    /** Asiento de débito por el monto indicado. */
    public static LedgerEntryRequest debit(UUID accountId, long amountMinor) {
        return new LedgerEntryRequest(accountId, LedgerEntryType.DEBIT, amountMinor);
    }

    /** Asiento de crédito por el monto indicado. */
    public static LedgerEntryRequest credit(UUID accountId, long amountMinor) {
        return new LedgerEntryRequest(accountId, LedgerEntryType.CREDIT, amountMinor);
    }
}
