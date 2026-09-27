package cl.antubank.ledger.service;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.ledger.persistence.AccountBalanceProjection;
import cl.antubank.ledger.persistence.LedgerEntryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de dominio que deriva el saldo de una cuenta a partir de sus asientos contables.
 *
 * <p>Implementa el Requisito 3, criterio 4: "cuando se solicite el saldo de una cuenta, el
 * sistema deberá derivarlo de la suma de sus asientos". El saldo no se persiste; se calcula
 * sumando el {@code amount_minor} con signo de todos los asientos de la cuenta (débito positivo,
 * crédito negativo) y reconstruyendo un {@link Money} por moneda.
 *
 * <p>Una cuenta puede tener asientos en varias monedas; en ese caso {@link #balances(UUID)}
 * devuelve un saldo por cada moneda. {@link #balance(UUID, Currency)} entrega el saldo de una
 * moneda puntual (cero si la cuenta no tiene asientos en ella).
 */
@Service
public class BalanceService {

    private final LedgerEntryRepository entryRepository;

    public BalanceService(LedgerEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    /**
     * Deriva el saldo de una cuenta en una moneda específica.
     *
     * @param accountId identificador de la cuenta.
     * @param currency  moneda del saldo a derivar.
     * @return el saldo derivado; {@code Money.zero(currency)} si la cuenta no tiene asientos.
     */
    @Transactional(readOnly = true)
    public AccountBalance balance(UUID accountId, Currency currency) {
        long totalMinor = entryRepository
                .sumByAccountAndCurrency(accountId, currency)
                .orElse(0L);
        return new AccountBalance(accountId, Money.ofMinor(totalMinor, currency));
    }

    /**
     * Deriva los saldos de una cuenta en todas las monedas en las que tenga asientos.
     *
     * @param accountId identificador de la cuenta.
     * @return un saldo por moneda; lista vacía si la cuenta no tiene asientos.
     */
    @Transactional(readOnly = true)
    public List<AccountBalance> balances(UUID accountId) {
        return entryRepository.sumByCurrency(accountId).stream()
                .map(projection -> toAccountBalance(accountId, projection))
                .toList();
    }

    private AccountBalance toAccountBalance(UUID accountId, AccountBalanceProjection projection) {
        Money money = Money.ofMinor(projection.totalMinor(), projection.currency());
        return new AccountBalance(accountId, money);
    }
}
