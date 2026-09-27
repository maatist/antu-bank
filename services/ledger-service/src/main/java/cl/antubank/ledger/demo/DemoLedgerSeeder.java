package cl.antubank.ledger.demo;

import cl.antubank.domain.demo.DemoAccount;
import cl.antubank.domain.demo.DemoDataset;
import cl.antubank.domain.money.Money;
import cl.antubank.ledger.domain.LedgerEntry;
import cl.antubank.ledger.domain.LedgerTransaction;
import cl.antubank.ledger.persistence.LedgerEntryRepository;
import cl.antubank.ledger.persistence.LedgerTransactionMapper;
import cl.antubank.ledger.persistence.LedgerTransactionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed automático de saldos iniciales en el ledger (tarea 12a.4, Requisito 12, criterio 3).
 *
 * <p>Se activa <strong>solo bajo el perfil {@code demo}</strong>. Para cada cuenta demo con saldo
 * de apertura (definidas en la fuente única de verdad {@link DemoDataset}, compartida con el
 * {@code account-service}), registra una transacción de doble entrada que <strong>debita</strong>
 * la cuenta del cliente (aumenta su saldo) y <strong>acredita</strong> por igual importe la cuenta
 * de patrimonio {@link DemoDataset#OPENING_BALANCE_ACCOUNT_ID}. Así:
 * <ul>
 *   <li>Cada asiento de apertura cuadra a cero (invariante Σ = 0, Requisito 3, criterio 1).</li>
 *   <li>El saldo derivado del cliente queda positivo y con historial visible en la demo.</li>
 *   <li>No se modifica ni borra nada: el ledger es append-only (Requisito 3, criterio 3).</li>
 * </ul>
 *
 * <p><strong>Idempotencia.</strong> Antes de asentar, se comprueba si la cuenta ya tiene asientos
 * en su moneda; si los tiene, se omite. De este modo reiniciar el servicio no duplica saldos.
 */
@Component
@Profile("demo")
public class DemoLedgerSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoLedgerSeeder.class);

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    public DemoLedgerSeeder(LedgerTransactionRepository transactionRepository,
                            LedgerEntryRepository entryRepository) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        int asentadas = 0;
        for (DemoAccount demo : DemoDataset.accounts()) {
            if (!demo.hasOpeningBalance()) {
                continue;
            }
            // Idempotencia: si la cuenta ya tiene asientos en su moneda, no se vuelve a asentar.
            boolean yaTieneSaldo = entryRepository
                    .sumByAccountAndCurrency(demo.id(), demo.currency())
                    .isPresent();
            if (yaTieneSaldo) {
                continue;
            }

            Money apertura = Money.ofMinor(demo.openingBalanceMinor(), demo.currency());
            // Débito al cliente (su saldo sube), crédito al patrimonio (contrapartida) => Σ = 0.
            LedgerTransaction tx = LedgerTransaction.of(
                    "seed-apertura-" + demo.id(),
                    List.of(
                            LedgerEntry.debit(demo.id(), apertura),
                            LedgerEntry.credit(DemoDataset.OPENING_BALANCE_ACCOUNT_ID, apertura)));
            transactionRepository.save(LedgerTransactionMapper.toEntity(tx));
            asentadas++;
        }

        if (asentadas > 0) {
            log.info("Seed demo: {} saldo(s) de apertura asentado(s) en ledger-service.", asentadas);
        } else {
            log.info("Seed demo: saldos de apertura ya presentes; no se asentó ninguno (idempotente).");
        }
    }
}
