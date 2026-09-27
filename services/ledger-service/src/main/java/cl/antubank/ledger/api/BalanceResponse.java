package cl.antubank.ledger.api;

import cl.antubank.domain.money.Currency;
import cl.antubank.ledger.service.AccountBalance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Saldo derivado de una cuenta en una moneda, expuesto por la API.
 *
 * <p>El saldo no se almacena: se deriva de la suma de los asientos de la cuenta
 * (ver requirements.md, Requisito 3, criterio 4). Puede ser negativo si la cuenta tiene
 * saldo acreedor neto.
 *
 * @param accountId   cuenta consultada.
 * @param currency    moneda del saldo.
 * @param amountMinor saldo en minor units (con signo).
 * @param amount      saldo en unidades mayores (con signo), según la escala de la moneda.
 */
@Schema(description = "Saldo derivado de una cuenta en una moneda")
public record BalanceResponse(

        @Schema(description = "Identificador de la cuenta")
        UUID accountId,

        @Schema(description = "Moneda del saldo")
        Currency currency,

        @Schema(description = "Saldo en minor units (con signo)", example = "38000")
        long amountMinor,

        @Schema(description = "Saldo en unidades mayores (con signo)", example = "38000")
        BigDecimal amount
) {

    /**
     * Construye la respuesta a partir del saldo derivado de dominio.
     */
    public static BalanceResponse from(AccountBalance balance) {
        return new BalanceResponse(
                balance.accountId(),
                balance.balance().currency(),
                balance.balance().toMinorUnits(),
                balance.balance().amount());
    }
}
