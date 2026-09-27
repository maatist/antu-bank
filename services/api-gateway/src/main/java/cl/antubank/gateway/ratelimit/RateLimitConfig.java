package cl.antubank.gateway.ratelimit;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del rate limiting del gateway (tarea 9.2, Requisito 9, criterio 3).
 *
 * <p>Habilita el enlace de {@link RateLimitProperties} y registra el {@link RateLimitingGlobalFilter}
 * como filtro global. El {@link Clock} se expone como bean para permitir un reloj determinista en
 * los tests (por defecto {@code Clock.systemUTC()}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    Clock rateLimitClock() {
        return Clock.systemUTC();
    }

    @Bean
    RateLimitingGlobalFilter rateLimitingGlobalFilter(RateLimitProperties properties, Clock rateLimitClock) {
        return new RateLimitingGlobalFilter(properties, rateLimitClock);
    }
}
