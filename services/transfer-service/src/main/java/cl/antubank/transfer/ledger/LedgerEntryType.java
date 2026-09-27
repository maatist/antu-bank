package cl.antubank.transfer.ledger;

/**
 * Naturaleza contable de un asiento en la solicitud al ledger-service.
 *
 * <p>Reproduce el contrato del ledger ({@code EntryType}): un asiento es débito o crédito. En una
 * transferencia se debita la cuenta origen y se acredita la destino, de modo que la transacción
 * quede balanceada (Σ = 0) como exige el ledger de doble entrada.
 */
public enum LedgerEntryType {

    /** Débito: sale dinero de la cuenta (cuenta origen de la transferencia). */
    DEBIT,

    /** Crédito: entra dinero a la cuenta (cuenta destino de la transferencia). */
    CREDIT
}
