package cl.antubank.ledger.service;

import cl.antubank.domain.money.Money;
import java.util.UUID;

/**
 * Saldo derivado de una cuenta en una moneda, calculado a partir de la suma de sus asientos.
 *
 * <p>El saldo nunca se almacena: es el resultado de {@code Σ (amount_minor con signo)} de todos
 * los asientos de la cuenta en la moneda indicada (Requisito 3, criterio 4). El signo del
 * {@link Money} refleja la naturaleza deudora o acreedora del saldo neto de la cuenta.
 *
 * @param accountId identificador de la cuenta.
 * @param balance   saldo derivado como value object monetario (puede ser negativo).
 */
public record AccountBalance(UUID accountId, Money balance) {
}
