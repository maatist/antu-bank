package cl.antubank.ledger;

import org.junit.jupiter.api.Test;

/**
 * Smoke test del scaffold del ledger-service: verifica que el contexto de Spring arranca
 * contra un PostgreSQL real (Testcontainers) y que Flyway aplica la migración baseline
 * sobre la base propia del servicio (database-per-service).
 */
class LedgerServiceApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
        // Si el contexto carga, la datasource, JPA y Flyway están correctamente cableados.
    }
}
