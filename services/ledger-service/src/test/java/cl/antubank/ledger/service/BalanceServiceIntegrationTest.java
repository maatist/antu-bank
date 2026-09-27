package cl.antubank.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.ledger.AbstractPostgresIntegrationTest;
import cl.antubank.ledger.domain.LedgerEntry;
import cl.antubank.ledger.domain.LedgerTransaction;
import cl.antubank.ledger.persistence.LedgerTransactionMapper;
import cl.antubank.ledger.persistence.LedgerTransactionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests de integración de la derivación de saldo (Requisito 3, criterio 4).
 *
 * <p>Verifican que el saldo de una cuenta se obtiene sumando los asientos con signo (débito
 * positivo, crédito negativo) sobre PostgreSQL real (Testcontainers), respetando la escala de
 * cada moneda al reconstruir el {@link Money}.
 */
@Transactional
class BalanceServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private BalanceService balanceService;

    private void registrar(LedgerTransaction tx) {
        transactionRepository.saveAndFlush(LedgerTransactionMapper.toEntity(tx));
    }

    @Test
    void saldoDeCuentaSinAsientosEsCero() {
        UUID cuentaSinMovimientos = UUID.randomUUID();

        AccountBalance saldo = balanceService.balance(cuentaSinMovimientos, Currency.CLP);

        assertThat(saldo.balance()).isEqualTo(Money.zero(Currency.CLP));
    }

    @Test
    void saldoSeDerivaDeLaSumaDeAsientos() {
        UUID cuenta = UUID.randomUUID();
        UUID contraparte = UUID.randomUUID();

        // La cuenta recibe dos débitos (entra dinero) y luego un crédito (sale dinero).
        registrar(LedgerTransaction.of("dep-1", List.of(
                LedgerEntry.debit(cuenta, Money.ofMajor(30_000, Currency.CLP)),
                LedgerEntry.credit(contraparte, Money.ofMajor(30_000, Currency.CLP)))));
        registrar(LedgerTransaction.of("dep-2", List.of(
                LedgerEntry.debit(cuenta, Money.ofMajor(20_000, Currency.CLP)),
                LedgerEntry.credit(contraparte, Money.ofMajor(20_000, Currency.CLP)))));
        registrar(LedgerTransaction.of("giro-1", List.of(
                LedgerEntry.credit(cuenta, Money.ofMajor(12_000, Currency.CLP)),
                LedgerEntry.debit(contraparte, Money.ofMajor(12_000, Currency.CLP)))));

        // Σ = +30.000 +20.000 −12.000 = 38.000 CLP.
        AccountBalance saldo = balanceService.balance(cuenta, Currency.CLP);
        assertThat(saldo.accountId()).isEqualTo(cuenta);
        assertThat(saldo.balance()).isEqualTo(Money.ofMajor(38_000, Currency.CLP));

        // La contraparte tiene el saldo espejo: −38.000 CLP.
        assertThat(balanceService.balance(contraparte, Currency.CLP).balance())
                .isEqualTo(Money.ofMajor(-38_000, Currency.CLP));
    }

    @Test
    void saldoPuedeSerNegativo() {
        UUID cuenta = UUID.randomUUID();
        UUID contraparte = UUID.randomUUID();

        registrar(LedgerTransaction.of("giro", List.of(
                LedgerEntry.credit(cuenta, Money.ofMajor(5_000, Currency.CLP)),
                LedgerEntry.debit(contraparte, Money.ofMajor(5_000, Currency.CLP)))));

        AccountBalance saldo = balanceService.balance(cuenta, Currency.CLP);
        assertThat(saldo.balance().isNegative()).isTrue();
        assertThat(saldo.balance()).isEqualTo(Money.ofMajor(-5_000, Currency.CLP));
    }

    @Test
    void acumulaSaldoUsdEnMinorUnitsRespetandoScale2() {
        // USD usa scale = 2 (centavos). Se acumulan varios asientos con parte decimal para
        // verificar que la derivación del saldo suma correctamente en minor units y reconstruye
        // el Money con la escala de la moneda (Requisito 1, criterio 3 + Requisito 3, criterio 4).
        UUID cuenta = UUID.randomUUID();
        UUID contraparte = UUID.randomUUID();

        registrar(LedgerTransaction.of("usd-1", List.of(
                LedgerEntry.debit(cuenta, Money.ofMajor("10.50", Currency.USD)),
                LedgerEntry.credit(contraparte, Money.ofMajor("10.50", Currency.USD)))));
        registrar(LedgerTransaction.of("usd-2", List.of(
                LedgerEntry.debit(cuenta, Money.ofMajor("0.25", Currency.USD)),
                LedgerEntry.credit(contraparte, Money.ofMajor("0.25", Currency.USD)))));
        // Sale un poco de dinero: crédito sobre la cuenta.
        registrar(LedgerTransaction.of("usd-3", List.of(
                LedgerEntry.credit(cuenta, Money.ofMajor("0.05", Currency.USD)),
                LedgerEntry.debit(contraparte, Money.ofMajor("0.05", Currency.USD)))));

        // Σ = +10,50 +0,25 −0,05 = US$10,70 (1070 centavos).
        AccountBalance saldo = balanceService.balance(cuenta, Currency.USD);
        assertThat(saldo.balance()).isEqualTo(Money.ofMajor("10.70", Currency.USD));
        assertThat(saldo.balance().toMinorUnits()).isEqualTo(1_070L);
        assertThat(saldo.balance().currency()).isEqualTo(Currency.USD);
    }

    @Test
    void deriveSaldoDeTransaccionConMultiplesAsientos() {
        // Una transacción "split": un débito grande balanceado por dos créditos a cuentas
        // distintas. El saldo de cada cuenta debe reflejar su asiento (Requisito 3, criterio 4).
        UUID origen = UUID.randomUUID();
        UUID destinoA = UUID.randomUUID();
        UUID destinoB = UUID.randomUUID();

        registrar(LedgerTransaction.of("split", List.of(
                LedgerEntry.debit(origen, Money.ofMajor(50_000, Currency.CLP)),
                LedgerEntry.credit(destinoA, Money.ofMajor(30_000, Currency.CLP)),
                LedgerEntry.credit(destinoB, Money.ofMajor(20_000, Currency.CLP)))));

        assertThat(balanceService.balance(origen, Currency.CLP).balance())
                .isEqualTo(Money.ofMajor(50_000, Currency.CLP));
        assertThat(balanceService.balance(destinoA, Currency.CLP).balance())
                .isEqualTo(Money.ofMajor(-30_000, Currency.CLP));
        assertThat(balanceService.balance(destinoB, Currency.CLP).balance())
                .isEqualTo(Money.ofMajor(-20_000, Currency.CLP));
    }

    @Test
    void derivaSaldosPorMonedaCuandoLaCuentaTieneVariasMonedas() {
        UUID cuenta = UUID.randomUUID();
        UUID contraparteClp = UUID.randomUUID();
        UUID contraparteUsd = UUID.randomUUID();

        registrar(LedgerTransaction.of("clp", List.of(
                LedgerEntry.debit(cuenta, Money.ofMajor(10_000, Currency.CLP)),
                LedgerEntry.credit(contraparteClp, Money.ofMajor(10_000, Currency.CLP)))));
        registrar(LedgerTransaction.of("usd", List.of(
                LedgerEntry.debit(cuenta, Money.ofMajor("150.50", Currency.USD)),
                LedgerEntry.credit(contraparteUsd, Money.ofMajor("150.50", Currency.USD)))));

        List<AccountBalance> saldos = balanceService.balances(cuenta);

        assertThat(saldos).extracting(AccountBalance::balance)
                .containsExactlyInAnyOrder(
                        Money.ofMajor(10_000, Currency.CLP),
                        Money.ofMajor("150.50", Currency.USD));
    }
}
