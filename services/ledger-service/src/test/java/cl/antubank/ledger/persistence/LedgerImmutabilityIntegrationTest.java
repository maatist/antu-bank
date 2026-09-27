package cl.antubank.ledger.persistence;

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
 * Tests de integración de la inmutabilidad del ledger (Requisito 3, criterio 3).
 *
 * <p>Verifican que, sobre PostgreSQL real, cualquier intento de UPDATE o DELETE sobre
 * {@code ledger_entry} o {@code ledger_transaction} es rechazado por los triggers de la
 * migración V3, garantizando el carácter append-only del libro mayor.
 */
@Transactional
class LedgerImmutabilityIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private LedgerTransactionRepository repository;

    @Autowired
    private EntityManager entityManager;

    private LedgerTransactionEntity persistirTransaccionBalanceada() {
        UUID cuentaA = UUID.randomUUID();
        UUID cuentaB = UUID.randomUUID();
        LedgerTransaction tx = LedgerTransaction.of(
                "inmutabilidad",
                List.of(
                        LedgerEntry.debit(cuentaA, Money.ofMajor(10_000, Currency.CLP)),
                        LedgerEntry.credit(cuentaB, Money.ofMajor(10_000, Currency.CLP))));
        LedgerTransactionEntity saved = repository.saveAndFlush(
                LedgerTransactionMapper.toEntity(tx));
        entityManager.clear();
        return saved;
    }

    @Test
    void rechazaUpdateSobreLedgerEntry() {
        LedgerTransactionEntity tx = persistirTransaccionBalanceada();
        UUID entryId = tx.getEntries().get(0).getId();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "UPDATE ledger_entry SET amount_minor = amount_minor + 1 WHERE id = ?")
                    .setParameter(1, entryId)
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class)
                .hasMessageContaining("inmutables");
    }

    @Test
    void rechazaDeleteSobreLedgerEntry() {
        LedgerTransactionEntity tx = persistirTransaccionBalanceada();
        UUID entryId = tx.getEntries().get(0).getId();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("DELETE FROM ledger_entry WHERE id = ?")
                    .setParameter(1, entryId)
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class)
                .hasMessageContaining("inmutables");
    }

    @Test
    void rechazaUpdateSobreLedgerTransaction() {
        LedgerTransactionEntity tx = persistirTransaccionBalanceada();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "UPDATE ledger_transaction SET reference = 'modificada' WHERE id = ?")
                    .setParameter(1, tx.getId())
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class)
                .hasMessageContaining("inmutables");
    }

    @Test
    void rechazaDeleteSobreLedgerTransaction() {
        LedgerTransactionEntity tx = persistirTransaccionBalanceada();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("DELETE FROM ledger_transaction WHERE id = ?")
                    .setParameter(1, tx.getId())
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class)
                .hasMessageContaining("inmutables");
    }
}
