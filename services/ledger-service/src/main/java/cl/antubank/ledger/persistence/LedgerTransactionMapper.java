package cl.antubank.ledger.persistence;

import cl.antubank.domain.money.Money;
import cl.antubank.ledger.domain.LedgerEntry;
import cl.antubank.ledger.domain.LedgerTransaction;
import java.util.List;
import java.util.UUID;

/**
 * Traduce entre el agregado de dominio {@link LedgerTransaction} y las entidades JPA
 * ({@link LedgerTransactionEntity} / {@link LedgerEntryEntity}).
 *
 * <p>El monto de dominio ({@link Money}) se convierte a minor units con signo para persistir:
 * débito positivo, crédito negativo. La reconstrucción invierte ese signo para volver a un
 * {@link Money} positivo con su {@link cl.antubank.ledger.domain.EntryType}.
 */
public final class LedgerTransactionMapper {

    private LedgerTransactionMapper() {
    }

    /**
     * Convierte un agregado de dominio (ya balanceado) en su entidad JPA.
     */
    public static LedgerTransactionEntity toEntity(LedgerTransaction transaction) {
        LedgerTransactionEntity entity = new LedgerTransactionEntity(
                transaction.id(), transaction.reference(), transaction.currency());

        for (LedgerEntry entry : transaction.entries()) {
            long signedMinor = entry.amount().toMinorUnits() * entry.type().sign();
            entity.addEntry(new LedgerEntryEntity(
                    UUID.randomUUID(),
                    entry.accountId(),
                    entry.type(),
                    entry.currency(),
                    signedMinor));
        }
        return entity;
    }

    /**
     * Reconstruye el agregado de dominio desde su entidad JPA, revalidando el invariante Σ = 0.
     */
    public static LedgerTransaction toDomain(LedgerTransactionEntity entity) {
        List<LedgerEntry> entries = entity.getEntries().stream()
                .map(LedgerTransactionMapper::toDomainEntry)
                .toList();
        return LedgerTransaction.of(entity.getId(), entity.getReference(), entries);
    }

    private static LedgerEntry toDomainEntry(LedgerEntryEntity entity) {
        long absMinor = Math.abs(entity.getAmountMinor());
        Money amount = Money.ofMinor(absMinor, entity.getCurrency());
        return LedgerEntry.of(entity.getAccountId(), entity.getEntryType(), amount);
    }
}
