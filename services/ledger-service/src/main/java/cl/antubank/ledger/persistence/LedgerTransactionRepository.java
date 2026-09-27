package cl.antubank.ledger.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA de transacciones contables de doble entrada.
 *
 * <p>Los asientos ({@link LedgerEntryEntity}) se persisten en cascada a través del agregado
 * {@link LedgerTransactionEntity}, por lo que no requieren un repositorio propio para escribir.
 */
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, UUID> {
}
