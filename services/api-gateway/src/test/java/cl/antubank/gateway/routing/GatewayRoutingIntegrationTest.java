package cl.antubank.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Test de integración del <b>enrutamiento</b> del gateway (tarea 9.4, Requisito 9, criterios 1 y 2).
 *
 * <p>Complementa a {@code ApiGatewayApplicationTests} (que solo verifica que las tres rutas están
 * <em>definidas</em>) probando el comportamiento observable de una ruta real: se levanta un
 * <b>downstream de prueba</b> (servidor reactor-netty en un puerto libre) que captura la petición
 * reenviada, y se apunta la ruta {@code transfer-service} a él vía {@code @DynamicPropertySource}.
 * Al pedir {@code /api/transfers/...} con un {@code Authorization: Bearer} se verifica que:
 * <ul>
 *   <li><b>Criterio 1 (enrutamiento):</b> la petición llega al downstream correcto (el gateway
 *       reenvía y responde con lo que el downstream devuelve).</li>
 *   <li><b>StripPrefix=1:</b> el segmento {@code /api} se elimina antes de reenviar, de modo que el
 *       downstream recibe su path nativo ({@code /transfers/...}).</li>
 *   <li><b>Criterio 2 (propagación de token):</b> el header {@code Authorization} entrante viaja
 *       intacto al downstream.</li>
 * </ul>
 *
 * <p>Como el downstream responde correctamente, el circuit breaker no se dispara: se ejercita el
 * camino feliz del enrutamiento (el fallback se cubre en {@code CircuitBreakerFallbackIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingIntegrationTest {

    /** Downstream de prueba: captura path y Authorization de la petición reenviada por el gateway. */
    private static DisposableServer downstream;
    private static final AtomicReference<String> capturedPath = new AtomicReference<>();
    private static final AtomicReference<String> capturedAuthorization = new AtomicReference<>();

    @BeforeAll
    static void startDownstream() {
        downstream = HttpServer.create()
                .host("localhost")
                .port(0)
                .handle((request, response) -> {
                    capturedPath.set(request.fullPath());
                    capturedAuthorization.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
                    return response.sendString(reactor.core.publisher.Mono.just(
                            "{\"downstream\":\"transfer-service\"}"));
                })
                .bindNow();
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) {
            downstream.disposeNow(Duration.ofSeconds(5));
        }
    }

    /**
     * Reapunta la ruta transfer-service al downstream vivo mediante la propiedad placeholder
     * {@code GATEWAY_TEST_TRANSFER_URI} que consume {@code application-test.yml}. Se sobreescribe el
     * placeholder (no un elemento indexado de la lista de rutas) para no romper el binding de la
     * lista {@code spring.cloud.gateway.routes}.
     */
    @DynamicPropertySource
    static void routeToLiveDownstream(DynamicPropertyRegistry registry) {
        registry.add("GATEWAY_TEST_TRANSFER_URI",
                () -> "http://localhost:" + downstream.port());
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void enrutaAlDownstreamCorrectoAplicandoStripPrefixYPropagandoElToken() {
        webTestClient.get().uri("/api/transfers/abc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-de-prueba")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.OK)
                .expectBody()
                .jsonPath("$.downstream").isEqualTo("transfer-service");

        // StripPrefix=1: el downstream recibe su path nativo, sin el prefijo /api.
        assertThat(capturedPath.get()).isEqualTo("/transfers/abc");
        // Propagación del token (criterio 2): el Authorization entrante llega intacto al downstream.
        assertThat(capturedAuthorization.get()).isEqualTo("Bearer token-de-prueba");
    }
}
