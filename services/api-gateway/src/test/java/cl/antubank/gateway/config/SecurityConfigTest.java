package cl.antubank.gateway.config;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Verifica la política de seguridad del borde del api-gateway (tarea 9.1, Requisito 9, criterio 2;
 * Requisito 8, criterio 5) sin depender de Keycloak.
 *
 * <p>Levanta un contexto reactivo mínimo que registra la cadena de seguridad de producción
 * ({@link SecurityConfig#securityWebFilterChain}) más dos endpoints de prueba que espejan las rutas
 * del gateway (uno público, uno de negocio). El {@link ReactiveJwtDecoder} se sustituye por un mock
 * (no se contacta el JWKS real) y la autenticación se simula con {@code mockJwt()}. Así la política
 * de autorización se prueba de forma aislada y determinista:
 * <ul>
 *   <li>Ruta pública ({@code /actuator/health}): accesible sin token.</li>
 *   <li>Ruta de negocio ({@code /api/**}) sin token: {@code 401}.</li>
 *   <li>Ruta de negocio con JWT válido: accesible.</li>
 * </ul>
 */
class SecurityConfigTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(SecurityConfig.class, TestBeans.class);
        context.refresh();

        this.client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void rutaPublicaDeActuatorEsAccesibleSinToken() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rutaDeNegocioSinTokenResponde401() {
        client.get().uri("/api/accounts/1")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rutaDeNegocioConTokenValidoEsAccesible() {
        client.mutateWith(mockJwt())
                .get().uri("/api/accounts/1")
                .exchange()
                .expectStatus().isOk();
    }

    /** Endpoints de prueba y decoder mock necesarios para montar la cadena de seguridad. */
    @Configuration
    @EnableWebFlux
    static class TestBeans {

        @Bean
        RouterFunction<ServerResponse> testRoutes() {
            return RouterFunctions.route()
                    .GET("/actuator/health", req -> ServerResponse.ok().bodyValue("UP"))
                    .GET("/api/accounts/1", req -> ServerResponse.ok().bodyValue("cuenta"))
                    .build();
        }

        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            // La validación real del JWT no se ejercita aquí; mockJwt() inyecta la autenticación.
            return mock(ReactiveJwtDecoder.class);
        }
    }
}
