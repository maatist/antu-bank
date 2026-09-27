package cl.antubank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del api-gateway de Antu Bank (tarea 9, Requisito 9).
 *
 * <p>Es el <b>único punto de entrada</b> del sistema: enruta el tráfico externo hacia los
 * microservicios internos mediante <b>Spring Cloud Gateway</b> (pila reactiva/WebFlux sobre
 * Netty). Valida el JWT emitido por Keycloak en el borde y <b>propaga</b> el header
 * {@code Authorization: Bearer <JWT>} a los servicios downstream (Requisito 9, criterios 1 y 2).
 *
 * <p>Las rutas hacia account-service, ledger-service y transfer-service se definen en
 * {@code application.yml}. El rate limiting y el circuit breaker (Resilience4j) corresponden a la
 * tarea 9.2; la capa GraphQL BFF, a la tarea 9.3.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
