package cl.antubank.gateway.graphql.model;

import cl.antubank.gateway.graphql.dto.BalanceDto;

/**
 * Modelo del tipo GraphQL {@code Money} (saldo/monto). Refleja el saldo derivado del ledger.
 *
 * @param amountMinor monto en minor units con signo (para CLP, pesos enteros).
 * @param amount      monto en unidades mayores con signo, como cadena para no perder precisión.
 * @param currency    moneda (CLP / USD / UF).
 */
public record MoneyModel(long amountMinor, String amount, String currency) {

    /** Construye el {@code Money} GraphQL a partir del saldo REST del ledger-service. */
    public static MoneyModel from(BalanceDto balance) {
        return new MoneyModel(
                balance.amountMinor(),
                balance.amount() != null ? balance.amount().toPlainString() : "0",
                balance.currency());
    }
}
