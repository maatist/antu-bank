package cl.antubank.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios del invariante de doble entrada Σ = 0 en el dominio.
 *
 * <p>Cubre el Requisito 3, criterios 1 (débito/crédito suman cero) y 2 (rechazar Σ ≠ 0).
 */
class LedgerTransactionTest {

    private static final UUID CUENTA_A = UUID.randomUUID();
    private static final UUID CUENTA_B = UUID.randomUUID();

    @Test
    void aceptaTransaccionBalanceadaDebitoIgualCredito() {
        // $10.000 CLP debitados de A y acreditados a B: Σ = 0.
        LedgerTransaction tx = LedgerTransaction.of(
                "transferencia-1",
                List.of(
                        LedgerEntry.debit(CUENTA_A, Money.ofMajor(10_000, Currency.CLP)),
                        LedgerEntry.credit(CUENTA_B, Money.ofMajor(10_000, Currency.CLP))));

        assertThat(tx.entries()).hasSize(2);
        assertThat(tx.currency()).isEqualTo(Currency.CLP);
        assertThat(tx.reference()).isEqualTo("transferencia-1");
    }

    @Test
    void aceptaTransaccionBalanceadaConMultiplesAsientos() {
        // Un débito grande balanceado por dos créditos que suman lo mismo.
        LedgerTransaction tx = LedgerTransaction.of(
                "split",
                List.of(
                        LedgerEntry.debit(CUENTA_A, Money.ofMajor(30_000, Currency.CLP)),
                        LedgerEntry.credit(CUENTA_B, Money.ofMajor(20_000, Currency.CLP)),
                        LedgerEntry.credit(CUENTA_A, Money.ofMajor(10_000, Currency.CLP))));

        assertThat(tx.entries()).hasSize(3);
    }

    @Test
    void rechazaTransaccionDesbalanceada() {
        // Débito 10.000 vs crédito 9.999: Σ ≠ 0 → rechazo (criterio 2).
        assertThatThrownBy(() -> LedgerTransaction.of(
                "desbalanceada",
                List.of(
                        LedgerEntry.debit(CUENTA_A, Money.ofMajor(10_000, Currency.CLP)),
                        LedgerEntry.credit(CUENTA_B, Money.ofMajor(9_999, Currency.CLP)))))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("desbalanceada");
    }

    @Test
    void rechazaTransaccionConUnSoloAsiento() {
        assertThatThrownBy(() -> LedgerTransaction.of(
                "single",
                List.of(LedgerEntry.debit(CUENTA_A, Money.ofMajor(10_000, Currency.CLP)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos dos asientos");
    }

    @Test
    void rechazaMezclaDeMonedasEnUnaTransaccion() {
        assertThatThrownBy(() -> LedgerTransaction.of(
                "multimoneda",
                List.of(
                        LedgerEntry.debit(CUENTA_A, Money.ofMajor(10_000, Currency.CLP)),
                        LedgerEntry.credit(CUENTA_B, Money.ofMajor(100, Currency.USD)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("misma moneda");
    }

    @Test
    void rechazaAsientoConMontoNegativo() {
        assertThatThrownBy(() ->
                LedgerEntry.debit(CUENTA_A, Money.ofMajor(-1, Currency.CLP)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no negativo");
    }

    @Test
    void rechazaAsientoConMontoCero() {
        assertThatThrownBy(() ->
                LedgerEntry.credit(CUENTA_A, Money.zero(Currency.CLP)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser cero");
    }

    @Test
    void aporteConSignoRespetaTipoDeAsiento() {
        LedgerEntry debito = LedgerEntry.debit(CUENTA_A, Money.ofMajor(5_000, Currency.CLP));
        LedgerEntry credito = LedgerEntry.credit(CUENTA_B, Money.ofMajor(5_000, Currency.CLP));

        assertThat(debito.signedAmount().signum()).isPositive();
        assertThat(credito.signedAmount().signum()).isNegative();
    }
}
