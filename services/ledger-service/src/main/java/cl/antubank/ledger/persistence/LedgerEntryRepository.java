package cl.antubank.ledger.persistence;

import cl.antubank.domain.money.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de lectura de asientos ({@link LedgerEntryEntity}) para derivar saldos.
 *
 * <p>La escritura de asientos ocurre en cascada a través del agregado
 * {@link LedgerTransactionEntity}; este repositorio existe únicamente para consultas de
 * derivación de saldo (Requisito 3, criterio 4: el saldo se deriva de la suma de los asientos).
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    /**
     * Suma los asientos de una cuenta agrupados por moneda.
     *
     * <p>Devuelve la suma con signo de {@code amount_minor} (débito positivo, crédito negativo)
     * por cada moneda en la que la cuenta tenga asientos. Una cuenta sin asientos devuelve una
     * lista vacía.
     *
     * @param accountId identificador de la cuenta.
     * @return una proyección por moneda con el total en minor units.
     */
    @Query("""
            SELECT new cl.antubank.ledger.persistence.AccountBalanceProjection(
                       e.currency, SUM(e.amountMinor))
              FROM LedgerEntryEntity e
             WHERE e.accountId = :accountId
             GROUP BY e.currency
            """)
    List<AccountBalanceProjection> sumByCurrency(@Param("accountId") UUID accountId);

    /**
     * Suma los asientos de una cuenta en una moneda específica.
     *
     * @param accountId identificador de la cuenta.
     * @param currency  moneda a consultar.
     * @return el total con signo en minor units, o vacío si la cuenta no tiene asientos en esa moneda.
     */
    @Query("""
            SELECT SUM(e.amountMinor)
              FROM LedgerEntryEntity e
             WHERE e.accountId = :accountId
               AND e.currency = :currency
            """)
    Optional<Long> sumByAccountAndCurrency(@Param("accountId") UUID accountId,
                                           @Param("currency") Currency currency);
}
