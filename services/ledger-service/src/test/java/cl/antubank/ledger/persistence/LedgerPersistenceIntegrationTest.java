package cl.antubank.ledger.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.ledger.AbstractPostgresIntegrationTest;
import cl.antubank.ledger.domain.LedgerEntry;
import cl.antubank.ledger.domain.LedgerTransaction;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Smoke test de persistencia del ledger: verifica que una transacción balanceada persiste
 * y que el refuerzo del invariante Σ = 0 en base de datos (trigger diferido de la migración V2)
 * rechaza asientos desbalanceados.
 */
@Transactional
class LedgerPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private LedgerTransactionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persisteYReconstruyeTransaccionBalanceada() {
        UUID cuentaA = UUID.randomUUID();
        UUID cuentaB = UUID.randomUUID();

        LedgerTransaction tx = LedgerTransaction.of(
                "smoke-balanceada",
                List.of(
                        LedgerEntry.debit(cuentaA, Money.ofMajor(10_000, Currency.CLP)),
                        LedgerEntry.credit(cuentaB, Money.ofMajor(10_000, Currency.CLP))));

        LedgerTransactionEntity saved = repository.saveAndFlush(
                LedgerTransactionMapper.toEntity(tx));
        entityManager.clear();

        LedgerTransactionEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEntries()).hasSize(2);
        assertThat(reloaded.getCurrency()).isEqualTo(Currency.CLP);

        LedgerTransaction domain = LedgerTransactionMapper.toDomain(reloaded);
        assertThat(domain.entries()).hasSize(2);
    }

    @Test
    void persisteTransaccionBalanceadaConMultiplesAsientos() {
        // El trigger diferido Σ = 0 debe aceptar un "split" balanceado de más de dos asientos
        // (un débito compensado por dos créditos) al evaluar la transacción como un todo.
        UUID origen = UUID.randomUUID();
        UUID destinoA = UUID.randomUUID();
        UUID destinoB = UUID.randomUUID();

        LedgerTransaction tx = LedgerTransaction.of(
                "smoke-split",
                List.of(
                        LedgerEntry.debit(origen, Money.ofMajor(50_000, Currency.CLP)),
                        LedgerEntry.credit(destinoA, Money.ofMajor(30_000, Currency.CLP)),
                        LedgerEntry.credit(destinoB, Money.ofMajor(20_000, Currency.CLP))));

        LedgerTransactionEntity saved = repository.saveAndFlush(
                LedgerTransactionMapper.toEntity(tx));
        // Fuerza la evaluación inmediata del trigger diferido: no debe lanzar.
        entityManager.createNativeQuery("SET CONSTRAINTS ALL IMMEDIATE").executeUpdate();
        entityManager.clear();

        LedgerTransactionEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEntries()).hasSize(3);
    }

    @Test
    void baseDeDatosRechazaTransaccionDesbalanceada() {
        // Se intenta insertar asientos que no suman cero saltándose el dominio (native insert)
        // para probar exclusivamente el refuerzo por trigger. El commit del flush debe fallar.
        UUID txId = UUID.randomUUID();
        UUID cuentaA = UUID.randomUUID();
        UUID cuentaB = UUID.randomUUID();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "INSERT INTO ledger_transaction (id, reference, currency) VALUES (?, ?, ?)")
                    .setParameter(1, txId)
                    .setParameter(2, "db-desbalanceada")
                    .setParameter(3, "CLP")
                    .executeUpdate();

            entityManager.createNativeQuery(
                            "INSERT INTO ledger_entry (id, transaction_id, account_id, entry_type, currency, amount_minor) "
                                    + "VALUES (?, ?, ?, 'DEBIT', 'CLP', 10000)")
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, txId)
                    .setParameter(3, cuentaA)
                    .executeUpdate();

            entityManager.createNativeQuery(
                            "INSERT INTO ledger_entry (id, transaction_id, account_id, entry_type, currency, amount_minor) "
                                    + "VALUES (?, ?, ?, 'CREDIT', 'CLP', -9999)")
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, txId)
                    .setParameter(3, cuentaB)
                    .executeUpdate();

            entityManager.flush();
            // El trigger es diferido (se evalúa al commit). Forzamos su evaluación inmediata
            // para materializar la violación dentro del test transaccional (que hará rollback).
            entityManager.createNativeQuery("SET CONSTRAINTS ALL IMMEDIATE").executeUpdate();
        }).isInstanceOf(Exception.class);
    }
}
