package cl.antubank.gateway.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifica el controlador de fallback del circuit breaker (tarea 9.2, Requisito 9, criterio 4).
 *
 * <p>Comprueba que las rutas {@code /fallback/**} responden {@code 503 Service Unavailable} con
 * cuerpo {@link org.springframework.http.ProblemDetail} (RFC 7807) y que el mensaje se localiza
 * es/en según {@code Accept-Language}. Se enlaza directamente el controlador con WebTestClient
 * (reactivo), sin levantar todo el gateway, para una prueba determinista.
 */
class FallbackControllerTest {

    private final WebTestClient client =
            WebTestClient.bindToController(new FallbackController()).build();

    @Test
    void fallbackDeCuentasResponde503ConProblemDetailEnEspanolPorDefecto() {
        client.get().uri("/fallback/accounts")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("Servicio temporalmente no disponible")
                .jsonPath("$.service").isEqualTo("account-service");
    }

    @Test
    void fallbackDeTransferenciasResponde503EnInglesConAcceptLanguageEn() {
        client.post().uri("/fallback/transfers")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Service temporarily unavailable")
                .jsonPath("$.service").isEqualTo("transfer-service");
    }

    @Test
    void fallbackDeMovimientosResponde503() {
        client.get().uri("/fallback/transactions")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.service").isEqualTo("ledger-service");
    }
}
