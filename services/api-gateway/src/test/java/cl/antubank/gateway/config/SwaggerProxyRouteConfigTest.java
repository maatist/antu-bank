package cl.antubank.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

/**
 * Verifica que, bajo el perfil {@code demo}, el gateway expone la documentación OpenAPI/Swagger de
 * forma pública a través del borde (tarea 12a.5, Requisito 12, criterio 4).
 *
 * <p>Se activan los perfiles {@code test} (evita depender de Keycloak/red al arrancar) y
 * {@code demo} (activa {@link SwaggerProxyRouteConfig}). El test comprueba dos cosas:
 * <ul>
 *   <li>Se registran las rutas de documentación {@code account-swagger-ui} y
 *       {@code account-openapi-docs} que reenvían la Swagger UI y el JSON de OpenAPI al
 *       account-service.</li>
 *   <li>Las rutas de negocio (account/ledger/transfer) <b>siguen presentes</b>: el
 *       {@link RouteLocator} de documentación se <b>suma</b> a las rutas YAML sin reemplazarlas.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({"test", "demo"})
class SwaggerProxyRouteConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void enPerfilDemoSeExponenLasRutasDeDocumentacionSinRomperLasDeNegocio() {
        List<Route> routes = Flux.from(routeLocator.getRoutes()).collectList().block();

        assertThat(routes).isNotNull();
        assertThat(routes)
                .extracting(Route::getId)
                // Documentación pública a través del gateway (tarea 12a.5).
                .contains("account-swagger-ui", "account-openapi-docs")
                // Las rutas de negocio no se pierden al sumar el RouteLocator de documentación.
                .contains("account-service", "ledger-service", "transfer-service");
    }
}
