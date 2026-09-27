package cl.antubank.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.http.codec.ServerCodecConfigurer;
import reactor.core.publisher.Mono;

/**
 * Verifica el rate limiting en memoria del gateway (tarea 9.2, Requisito 9, criterio 3).
 *
 * <p>Ejercita directamente el {@link RateLimitingGlobalFilter} con un {@link Clock} fijo (ventana
 * estable) para que la prueba sea determinista: se disparan {@code capacity} peticiones permitidas
 * y la petición {@code capacity + 1} debe ser rechazada con {@code 429 Too Many Requests} y header
 * {@code Retry-After}. Con el reloj fijo todas caen en la misma ventana.
 */
class RateLimitingGlobalFilterTest {

    /** Reloj fijo: todas las peticiones caen en la misma ventana → conteo determinista. */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    private ServerWebExchange exchangeFromSameClient() {
        // Misma IP remota en todas las peticiones → misma clave de rate limit.
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts/1")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.7", 12345))
                .build();
        return new DefaultServerWebExchange(
                request,
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse(),
                new DefaultWebSessionManager(),
                ServerCodecConfigurer.create(),
                new AcceptHeaderLocaleContextResolver());
    }

    @Test
    void permiteHastaLaCapacidadYlaSiguientePeticionResponde429() {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(true);
        props.setCapacity(5);
        props.setWindowSeconds(1);

        RateLimitingGlobalFilter filter = new RateLimitingGlobalFilter(props, FIXED_CLOCK);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // Las primeras 'capacity' peticiones pasan (el chain se invoca y no se marca status de error).
        for (int i = 0; i < props.getCapacity(); i++) {
            ServerWebExchange exchange = exchangeFromSameClient();
            filter.filter(exchange, chain).block();
            assertThat(exchange.getResponse().getStatusCode())
                    .as("petición permitida #%d no debe marcar 429", i + 1)
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        // La petición capacity + 1 debe ser rechazada con 429 y Retry-After.
        ServerWebExchange rejected = exchangeFromSameClient();
        filter.filter(rejected, chain).block();

        assertThat(rejected.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rejected.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
    }

    @Test
    void deshabilitadoNoAplicaLimite() {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(false);
        props.setCapacity(1);

        RateLimitingGlobalFilter filter = new RateLimitingGlobalFilter(props, FIXED_CLOCK);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (int i = 0; i < 10; i++) {
            ServerWebExchange exchange = exchangeFromSameClient();
            filter.filter(exchange, chain).block();
            assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
