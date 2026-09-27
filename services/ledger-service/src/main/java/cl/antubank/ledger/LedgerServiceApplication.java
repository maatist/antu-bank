package cl.antubank.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del ledger-service de Antu Bank.
 *
 * <p>Lleva la contabilidad de doble entrada del banco: registra asientos (débito/crédito)
 * inmutables y deriva los saldos de cada cuenta a partir de ellos. Persiste en su propia
 * base PostgreSQL (database-per-service), independiente de los demás microservicios.
 */
@SpringBootApplication
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
