package cl.antubank.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del transfer-service de Antu Bank.
 *
 * <p>Orquesta transferencias entre cuentas en CLP de forma <b>idempotente</b>: cada solicitud
 * viaja con un header {@code Idempotency-Key} que se persiste junto al resultado, de modo que un
 * reintento por red inestable nunca duplique el movimiento (ver requirements.md, Requisito 4).
 *
 * <p>Persiste en su propia base PostgreSQL (database-per-service), independiente de los demás
 * microservicios. El endpoint {@code POST /transfers} y la lógica de idempotencia/validación de
 * fondos se agregan en las tareas 4.2–4.4.
 */
@SpringBootApplication
public class TransferServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransferServiceApplication.class, args);
    }
}
