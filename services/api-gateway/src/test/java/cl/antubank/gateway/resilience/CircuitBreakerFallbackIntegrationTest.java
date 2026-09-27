package cl.antubank.gateway.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Test de integración del circuit breaker con fallback a través del gateway completo
 * (tarea 9.2, Requisito 9, criterio 4).
 *
 * <p>Bajo el perfil {@code test}, las rutas apuntan a puertos donde <b>nadie escucha</b> (dead) y
 * llevan el filtro {@code CircuitBreaker} con {@code fallbackUri}. Al pedir {@code /api/accounts/1}
 * el downstream falla (conexión rechazada / timeout), el breaker desvía la petición al
 * {@link FallbackController} y el cliente recibe {@code 503 Service Unavailable} con
 * {@code ProblemDetail}. Se ejercita el flujo real del gateway sin servicios downstream reales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CircuitBreakerFallbackIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private WebTestClient webTestClient;

    @Test
    void cuandoElDownstreamNoRespondeElBreakerDesviaAlFallback() {
        webTestClient.get().uri("/api/accounts/1")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.service").isEqualTo("account-service");
    }
}
