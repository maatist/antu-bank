package cl.antubank.domain.demo;

import cl.antubank.domain.account.AccountType;
import cl.antubank.domain.account.ChileanBank;
import cl.antubank.domain.identity.Rut;
import cl.antubank.domain.money.Currency;
import java.util.UUID;

/**
 * Descripción de una cuenta demo del seed automático (tarea 12a.4, Requisito 12, criterio 3).
 *
 * <p>Es un value object de dominio puro (sin Spring) que describe una cuenta bancaria de ejemplo
 * con datos chilenos realistas: RUT válido (DV módulo 11), nombre chileno, banco de la plaza local
 * y un saldo inicial en minor units. El {@code id} es un UUID <strong>fijo y determinístico</strong>
 * para que la misma cuenta pueda sembrarse de forma coherente y <em>idempotente</em> en dos
 * servicios independientes:
 * <ul>
 *   <li>{@code account-service} crea la cuenta (metadatos: titular, tipo, banco, moneda).</li>
 *   <li>{@code ledger-service} le asienta el saldo inicial referenciando el mismo {@code id}
 *       como {@code accountId} del asiento contable.</li>
 * </ul>
 *
 * <p>Al compartir el identificador vía este dataset común, el saldo derivado en el ledger queda
 * ligado a la cuenta real sin necesidad de una llamada entre servicios durante el arranque.
 *
 * @param id             identificador fijo de la cuenta (compartido entre servicios).
 * @param holderRut      RUT del titular (validado módulo 11 al construirse).
 * @param holderName     nombre del titular (dato chileno realista).
 * @param accountType    tipo de cuenta (corriente/vista/ahorro).
 * @param bank           banco de la plaza chilena.
 * @param currency       moneda de la cuenta y del saldo inicial.
 * @param openingBalanceMinor saldo inicial en minor units de la moneda (0 = sin saldo inicial).
 */
public record DemoAccount(
        UUID id,
        Rut holderRut,
        String holderName,
        AccountType accountType,
        ChileanBank bank,
        Currency currency,
        long openingBalanceMinor) {

    public DemoAccount {
        if (id == null) {
            throw new IllegalArgumentException("id no puede ser null");
        }
        if (holderRut == null) {
            throw new IllegalArgumentException("holderRut no puede ser null");
        }
        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("holderName no puede ser vacío");
        }
        if (accountType == null || bank == null || currency == null) {
            throw new IllegalArgumentException("accountType, bank y currency no pueden ser null");
        }
        if (openingBalanceMinor < 0) {
            throw new IllegalArgumentException("openingBalanceMinor no puede ser negativo");
        }
    }

    /**
     * @return {@code true} si la cuenta debe recibir un saldo inicial en el ledger.
     */
    public boolean hasOpeningBalance() {
        return openingBalanceMinor > 0;
    }
}
