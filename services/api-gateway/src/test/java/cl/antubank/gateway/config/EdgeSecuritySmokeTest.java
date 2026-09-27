package cl.antubank.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Prueba end-to-end del <b>borde de seguridad</b> del api-gateway (tarea 13.5, Requisito 13,
 * criterio 5). Levanta la aplicación real en un puerto aleatorio bajo el perfil {@code prod}, de
 * modo que se ejercita la <b>cadena de seguridad de producción</b>
 * ({@link SecurityConfig#securityWebFilterChain}, perfil {@code !test}): cabeceras de seguridad,
 * política CORS acotada y autorización de rutas, sobre un servidor Netty real (con esquema/host
 * verdaderos, necesarios para que el procesamiento CORS sea fiel).
 *
 * <p>Para no depender de un Keycloak en ejecución se sustituye el {@link ReactiveJwtDecoder} por un
 * stub y se fija {@code jwk-set-uri} (evita el OIDC discovery al arrancar). Estas pruebas no
 * adjuntan token, así que la validación real del JWT no se ejercita aquí (la cubre
 * {@link SecurityConfigTest} y los {@code AuthorizationSecurityTest} de los servicios); lo que se
 * verifica es que las rutas protegidas respondan {@code 401} sin token.
 *
 * <p>El origen permitido para CORS se inyecta con {@code @DynamicPropertySource}
 * ({@code ALLOWED_ORIGINS = http://localhost:3000}); un origen distinto queda fuera de la lista.
 *
 * <p><b>Limitación:</b> se verifica la respuesta del servidor (cabeceras y respuesta al preflight),
 * no la aplicación de la política CORS por un navegador real ni un E2E vivo de los 6 servicios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@org.springframework.context.annotation.Import(EdgeSecuritySmokeTest.JwtStubConfig.class)
class EdgeSecuritySmokeTest {

    private static final String ORIGEN_PERMITIDO = "http://localhost:3000";
    private static final String ORIGEN_NO_PERMITIDO = "https://atacante.example";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Origen permitido para CORS (el default sería localhost:3000 igualmente; explícito aquí
        // para que el test documente la política que ejercita).
        registry.add("ALLOWED_ORIGINS", () -> ORIGEN_PERMITIDO);
        // jwk-set-uri en lugar de issuer-uri: el ReactiveJwtDecoder se crea de forma perezosa y NO
        // se contacta Keycloak al arrancar (además el decoder real se reemplaza por un stub).
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:0/dummy-jwks");
        // Rutas /api/** a un puerto muerto: no se alcanzan porque el test no envía token (401 antes).
        registry.add("ACCOUNT_SERVICE_URI", () -> "http://localhost:18082");
        registry.add("LEDGER_SERVICE_URI", () -> "http://localhost:18083");
        registry.add("TRANSFER_SERVICE_URI", () -> "http://localhost:18084");
    }

    @Autowired
    private WebTestClient client;

    // --- Cabeceras de seguridad -------------------------------------------------------------

    @Test
    void lasRespuestasIncluyenCabecerasDeSeguridad() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().exists("Referrer-Policy")
                .expectHeader().exists("Content-Security-Policy");
        // Nota: HSTS (Strict-Transport-Security) NO se verifica aquí porque Spring Security solo la
        // emite sobre conexiones HTTPS y el test usa HTTP plano. En el despliegue el TLS lo termina
        // el borde (PaaS/Vercel/ALB), donde la cabecera sí aplica. Ver docs/security.
    }

    // --- CORS -------------------------------------------------------------------------------

    @Test
    void preflightDesdeOrigenPermitidoEsAceptado() {
        client.options().uri("/api/accounts/1")
                .header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO);
    }

    @Test
    void preflightDesdeOrigenNoPermitidoEsRechazado() {
        client.options().uri("/api/accounts/1")
                .header(HttpHeaders.ORIGIN, ORIGEN_NO_PERMITIDO)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                // Un origen no permitido produce un preflight fallido (403) sin cabecera
                // Access-Control-Allow-Origin: el navegador bloquearía la petición real.
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    // --- Autorización de rutas --------------------------------------------------------------

    @Test
    void rutaPublicaGraphiqlEsAccesibleSinToken() {
        client.get().uri("/graphiql")
                .exchange()
                // La UI GraphiQL es pública (200) o redirige a su recurso; en cualquier caso NO es
                // 401. Se afirma que no exige autenticación.
                .expectStatus().value(status -> {
                    if (status == HttpStatus.UNAUTHORIZED.value()) {
                        throw new AssertionError("GraphiQL no debe exigir token, fue 401");
                    }
                });
    }

    @Test
    void rutaDeNegocioSinTokenResponde401() {
        client.get().uri("/api/accounts/1")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void graphqlSinTokenResponde401() {
        client.post().uri("/graphql")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"{ __typename }\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Stub del decoder de JWT: evita depender de Keycloak. No se usa (los tests no envían token). */
    @TestConfiguration
    static class JwtStubConfig {

        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.error(
                    new org.springframework.security.oauth2.jwt.BadJwtException("stub"));
        }
    }
}
