package cl.antubank.transfer.ledger;

import cl.antubank.domain.money.Currency;
import java.util.List;

/**
 * Solicitud para registrar una transacción de doble entrada en el ledger-service.
 *
 * <p>Reproduce el contrato del ledger ({@code RegisterTransactionRequest}). Para una transferencia
 * confirmada contiene dos asientos balanceados: débito en la cuenta origen y crédito en la cuenta
 * destino por el mismo monto, de modo que Σ = 0.
 *
 * @param reference referencia de negocio (aquí, el id de la transferencia que la origina).
 * @param currency  moneda de todos los asientos (CLP como moneda principal, Requisito 4).
 * @param entries   asientos de la transacción (débito + crédito).
 */
public record RegisterTransactionRequest(String reference, Currency currency,
                                         List<LedgerEntryRequest> entries) {
}
