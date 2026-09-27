package cl.antubank.transfer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para tests de integración que levanta un PostgreSQL real vía Testcontainers.
 * El contenedor es estático (se reutiliza entre tests de la misma JVM).
 *
 * <p>Se activa el perfil {@code test} (tarea 8.2): habilita la cadena de seguridad permisiva y
 * evita el OIDC discovery contra Keycloak al arrancar el contexto, de modo que los tests de API
 * que llaman sin token siguen en verde.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("transfer")
                    .withUsername("transfer")
                    .withPassword("transfer");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Prácticamente desactiva el poller programado del outbox relay durante los tests: los
        // escenarios que lo ejercitan invocan publishPending() de forma determinista. Así el
        // scheduler no genera intentos de conexión ni ruido en tests ajenos al relay.
        registry.add("outbox.relay.poll-interval-ms", () -> "3600000");
    }
}
