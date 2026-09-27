package cl.antubank.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Rutas del gateway que exponen la documentación OpenAPI/Swagger de forma <b>pública</b> a través
 * del borde (tarea 12a.5, Requisito 12, criterio 4). Activas <b>solo bajo el perfil {@code demo}</b>.
 *
 * <p><b>Motivo.</b> En la topología demo el api-gateway es el único servicio público; los
 * microservicios REST (account/ledger/transfer) son privados y no se alcanzan desde internet. El
 * propio gateway corre sobre WebFlux/Spring Cloud Gateway y NO incorpora springdoc (no sirve
 * Swagger por sí mismo). Para que la Swagger UI sea navegable en
 * {@code https://<gateway>/swagger-ui.html} (checklist de despliegue) se reenvían aquí, a través
 * del gateway, la UI de Swagger y el JSON de OpenAPI del <b>account-service</b> (contrato REST de
 * referencia del sistema).
 *
 * <p><b>Por qué en Java y no en {@code application-demo.yml}.</b> Spring Cloud Gateway lee las rutas
 * de {@code spring.cloud.gateway.routes} como una lista; al declararla también en un archivo de
 * perfil, esa lista <b>reemplaza</b> (no fusiona) la del {@code application.yml} base, lo que
 * borraría las rutas de negocio. Un {@link RouteLocator} adicional se <b>suma</b> a las rutas YAML
 * sin tocarlas, y con {@code @Profile("demo")} solo existe en el despliegue público.
 *
 * <p><b>Diseño de las rutas.</b> Sin {@code StripPrefix}: los paths de springdoc
 * ({@code /swagger-ui.html}, {@code /swagger-ui/**}, {@code /v3/api-docs}, {@code /v3/api-docs/**})
 * son idénticos en el gateway y en el downstream, de modo que la Swagger UI cargada desde el gateway
 * resuelve su propio JSON de OpenAPI por el mismo origen. Sin {@code CircuitBreaker}: la
 * documentación es best-effort, no un endpoint de negocio.
 *
 * <p><b>No debilita la protección de datos.</b> Solo se reenvían los paths de documentación, que en
 * el account-service son {@code permitAll} (ver su {@code SecurityConfig}); el gateway también los
 * permite sin token (ver {@link SecurityConfig#PUBLIC_PATHS}). Los endpoints de negocio
 * ({@code /api/**}) siguen exigiendo un JWT válido tanto en el borde como en el servicio.
 *
 * <p>El playground GraphQL (GraphiQL) NO necesita rutas: la UI {@code /graphiql} y el endpoint
 * {@code /graphql} los sirve el propio gateway (Spring for GraphQL) en todos los perfiles, y
 * {@code SecurityConfig} ya permite {@code /graphiql/**} sin token.
 */
@Configuration
@Profile("demo")
public class SwaggerProxyRouteConfig {

    /** URI interna del account-service; misma variable que usan las rutas y el BFF. */
    private final String accountServiceUri;

    public SwaggerProxyRouteConfig(
            @Value("${ACCOUNT_SERVICE_URI:http://account-service:8082}") String accountServiceUri) {
        this.accountServiceUri = accountServiceUri;
    }

    @Bean
    RouteLocator swaggerProxyRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Swagger UI del account-service (HTML + recursos estáticos) reenviada tal cual.
                .route("account-swagger-ui", r -> r
                        .path("/swagger-ui.html", "/swagger-ui/**")
                        .uri(accountServiceUri))
                // JSON de OpenAPI que la Swagger UI consume desde el mismo origen (el gateway).
                .route("account-openapi-docs", r -> r
                        .path("/v3/api-docs", "/v3/api-docs/**")
                        .uri(accountServiceUri))
                .build();
    }
}
