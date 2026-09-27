package cl.antubank.ledger.persistence;

import cl.antubank.domain.money.Currency;

/**
 * Proyección de la suma de asientos de una cuenta en una moneda determinada.
 *
 * <p>El saldo de una cuenta no se almacena: se deriva sumando el {@code amount_minor} con signo
 * de todos sus asientos (débito &gt; 0, crédito &lt; 0), agrupado por moneda
 * (ver requirements.md, Requisito 3, criterio 4). Esta proyección transporta ese total en
 * minor units para luego reconstruir un {@link cl.antubank.domain.money.Money}.
 *
 * @param currency        moneda de los asientos agregados.
 * @param totalMinor      suma de {@code amount_minor} con signo, en minor units.
 */
public record AccountBalanceProjection(Currency currency, long totalMinor) {
}
