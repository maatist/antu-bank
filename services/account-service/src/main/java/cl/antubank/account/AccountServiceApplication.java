package cl.antubank.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del account-service de Antu Bank.
 *
 * <p>Gestiona cuentas bancarias asociadas a un RUT chileno, con tipo de cuenta y banco de la
 * plaza local. Persiste en su propia base PostgreSQL (database-per-service).
 */
@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
