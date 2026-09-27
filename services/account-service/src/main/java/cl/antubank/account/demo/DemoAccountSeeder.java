package cl.antubank.account.demo;

import cl.antubank.account.persistence.AccountEntity;
import cl.antubank.account.persistence.AccountRepository;
import cl.antubank.account.persistence.AccountStatus;
import cl.antubank.domain.demo.DemoAccount;
import cl.antubank.domain.demo.DemoDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seed automático de cuentas demo (tarea 12a.4, Requisito 12, criterio 3).
 *
 * <p>Se activa <strong>solo bajo el perfil {@code demo}</strong> (despliegue público reducido),
 * de modo que el entorno local (database-per-service estricto) nunca se contamina con datos de
 * ejemplo. Al arrancar, siembra un conjunto de cuentas con datos chilenos realistas — RUT válidos
 * (DV módulo 11), nombres chilenos y bancos reales de la plaza local — tomados de la fuente única
 * de verdad {@link DemoDataset}, compartida con el {@code ledger-service} que les asienta el saldo.
 *
 * <p><strong>Idempotencia.</strong> Cada cuenta tiene un UUID fijo; antes de insertar se verifica
 * su existencia por id, por lo que reiniciar el servicio no duplica ni sobrescribe cuentas. Así el
 * seed puede correr en cada arranque del contenedor sin efectos colaterales.
 */
@Component
@Profile("demo")
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final AccountRepository repository;

    public DemoAccountSeeder(AccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        int creadas = 0;
        for (DemoAccount demo : DemoDataset.accounts()) {
            if (repository.existsById(demo.id())) {
                continue; // Idempotencia: la cuenta ya fue sembrada en un arranque anterior.
            }
            repository.save(new AccountEntity(
                    demo.id(),
                    demo.holderRut(),
                    demo.holderName(),
                    demo.accountType(),
                    demo.bank(),
                    demo.currency(),
                    AccountStatus.ACTIVE));
            creadas++;
        }

        if (creadas > 0) {
            log.info("Seed demo: {} cuenta(s) chilena(s) sembrada(s) en account-service.", creadas);
        } else {
            log.info("Seed demo: cuentas ya presentes; no se sembró ninguna (idempotente).");
        }
    }
}
