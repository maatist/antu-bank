package cl.antubank.transfer;

import org.junit.jupiter.api.Test;

/**
 * Smoke test del scaffold del transfer-service: verifica que el contexto de Spring arranca
 * contra un PostgreSQL real (Testcontainers) y que Flyway aplica las migraciones (baseline +
 * tablas transfer/idempotency_key) sobre la base propia del servicio (database-per-service).
 */
class TransferServiceApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
        // Si el contexto carga, la datasource, JPA y Flyway están correctamente cableados
        // y el esquema validado por Hibernate coincide con las migraciones.
    }
}
