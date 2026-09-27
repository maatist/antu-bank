package cl.antubank.gateway;

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
 * Tests de carga de contexto y de configuración de rutas del api-gateway (tarea 9.1).
 *
 * <p>Se ejecutan bajo el perfil {@code test}, que evita depender de Keycloak: el contexto arranca
 * sin contactar la red (ver {@code application-test.yml}). Verifican dos cosas:
 * <ul>
 *   <li>El contexto reactivo (WebFlux + Spring Cloud Gateway) se levanta correctamente.</li>
 *   <li>Existen las tres rutas de negocio esperadas (account, ledger, transfer), lo que confirma
 *       el enrutamiento del Requisito 9, criterio 1.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void elContextoDelGatewayArranca() {
        // El solo hecho de inyectar el RouteLocator prueba que el contexto reactivo cargó.
        assertThat(routeLocator).isNotNull();
    }

    @Test
    void seDefinenLasTresRutasDeNegocio() {
        List<Route> routes = Flux.from(routeLocator.getRoutes()).collectList().block();

        assertThat(routes).isNotNull();
        assertThat(routes)
                .extracting(Route::getId)
                .contains("account-service", "ledger-service", "transfer-service");
    }
}
