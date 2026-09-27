package cl.antubank.gateway.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate limiting en el borde como {@link GlobalFilter} en memoria (tarea 9.2, Requisito 9,
 * criterio 3).
 *
 * <p><b>Decisión de diseño (opción b).</b> Spring Cloud Gateway trae el filtro
 * {@code RequestRateLimiter} basado en Redis, que exigiría levantar una instancia Redis. Para una
 * demo de portafolio se prefiere un limitador <b>en memoria</b> sin infraestructura extra: un
 * <b>token bucket de ventana fija</b> por cliente. Es simple, determinista y no añade dependencias
 * ni contenedores. La contrapartida conocida es que el conteo es <b>por instancia</b> (no
 * distribuido); en un despliegue multi-instancia real se migraría al limitador basado en Redis. El
 * límite es configurable vía {@link RateLimitProperties}.
 *
 * <p><b>Clave del cliente.</b> Se usa el {@code sub} del JWT (usuario autenticado) cuando está
 * presente en el header {@code Authorization}; si no, se cae a la IP remota. Así el límite es por
 * usuario cuando hay identidad, y por origen de red en tráfico anónimo.
 *
 * <p>Al superarse el límite dentro de la ventana, la respuesta es {@code 429 Too Many Requests} con
 * el header {@code Retry-After} indicando los segundos hasta el reinicio de la ventana.
 *
 * <p>Se ejecuta con orden alto (antes que el enrutamiento) para rechazar temprano el exceso de
 * tráfico sin invocar downstream.
 */
public class RateLimitingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingGlobalFilter.class);

    private final RateLimitProperties properties;
    private final Clock clock;

    /** Estado por cliente: ventana actual (epoch en unidades de ventana) y peticiones consumidas. */
    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();

    public RateLimitingGlobalFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String clientKey = resolveClientKey(exchange.getRequest());
        long windowSeconds = Math.max(1, properties.getWindowSeconds());
        long currentWindow = clock.instant().getEpochSecond() / windowSeconds;

        boolean allowed = tryConsume(clientKey, currentWindow);
        if (allowed) {
            return chain.filter(exchange);
        }

        return tooManyRequests(exchange, windowSeconds, currentWindow);
    }

    /**
     * Intenta consumir una petición para el cliente en la ventana actual. Reinicia el conteo cuando
     * la ventana avanza. Devuelve {@code true} si aún queda cupo (petición permitida).
     */
    private boolean tryConsume(String clientKey, long currentWindow) {
        Window window = buckets.compute(clientKey, (key, existing) -> {
            if (existing == null || existing.window != currentWindow) {
                return new Window(currentWindow);
            }
            return existing;
        });
        int used = window.count.incrementAndGet();
        return used <= properties.getCapacity();
    }

    /** Responde 429 con {@code Retry-After} = segundos restantes hasta el fin de la ventana. */
    private Mono<Void> tooManyRequests(ServerWebExchange exchange, long windowSeconds,
            long currentWindow) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        long nextWindowStart = (currentWindow + 1) * windowSeconds;
        long retryAfter = Math.max(1, nextWindowStart - clock.instant().getEpochSecond());
        response.getHeaders().add(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        if (log.isDebugEnabled()) {
            log.debug("Rate limit excedido; se responde 429 (retry-after={}s)", retryAfter);
        }
        return response.setComplete();
    }

    /**
     * Deriva la clave del cliente: {@code sub} del JWT si viene un Bearer token válido en estructura;
     * en caso contrario la IP remota. No valida la firma (eso ya lo hace la cadena de seguridad); solo
     * lee el {@code sub} para segmentar el conteo por usuario cuando hay identidad.
     */
    private String resolveClientKey(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String subject = extractSubject(authorization);
        if (subject != null) {
            return "user:" + subject;
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return "ip:" + request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "anonymous";
    }

    /** Extrae el claim {@code sub} de un JWT Bearer sin validar la firma; {@code null} si no aplica. */
    private static String extractSubject(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            // Extracción mínima del claim "sub" sin añadir un parser JSON completo.
            int idx = json.indexOf("\"sub\"");
            if (idx < 0) {
                return null;
            }
            int colon = json.indexOf(':', idx);
            int firstQuote = json.indexOf('"', colon + 1);
            int secondQuote = json.indexOf('"', firstQuote + 1);
            if (firstQuote < 0 || secondQuote < 0) {
                return null;
            }
            return json.substring(firstQuote + 1, secondQuote);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public int getOrder() {
        // Alta prioridad: rechazar el exceso antes del enrutamiento a downstream.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /** Ventana fija de conteo para un cliente. */
    private static final class Window {
        private final long window;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long window) {
            this.window = window;
        }
    }
}
