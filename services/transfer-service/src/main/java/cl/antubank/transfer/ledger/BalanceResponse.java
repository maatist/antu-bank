package cl.antubank.transfer.ledger;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Saldo derivado de una cuenta, tal como lo expone el ledger-service
 * ({@code GET /transactions/balances/{accountId}}).
 *
 * <p>Solo se mapean los campos necesarios para la validación de fondos; el resto se ignora para no
 * acoplar el cliente a cambios menores del contrato. El saldo se reconstituye como {@link Money}.
 *
 * @param accountId   cuenta consultada.
 * @param currency    moneda del saldo.
 * @param amountMinor saldo en minor units (con signo).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BalanceResponse(UUID accountId, Currency currency, long amountMinor) {

    /**
     * @return el saldo como value object {@link Money} en su moneda.
     */
    public Money toMoney() {
        return Money.ofMinor(amountMinor, currency);
    }
}
