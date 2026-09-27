package cl.antubank.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades configurables del rate limiting del gateway (tarea 9.2, Requisito 9, criterio 3).
 *
 * <p>Se enlazan bajo el prefijo {@code antubank.gateway.rate-limit} en {@code application.yml}, de
 * modo que los límites son ajustables por entorno sin recompilar:
 *
 * <pre>
 * antubank:
 *   gateway:
 *     rate-limit:
 *       enabled: true
 *       capacity: 20        # peticiones permitidas por ventana
 *       window-seconds: 1   # duración de la ventana en segundos
 * </pre>
 *
 * <p>El límite se aplica por cliente (ver {@link RateLimitingGlobalFilter}), y al excederse el
 * gateway responde {@code 429 Too Many Requests}.
 */
@ConfigurationProperties(prefix = "antubank.gateway.rate-limit")
public class RateLimitProperties {

    /** Habilita o deshabilita el rate limiting en el borde. Por defecto {@code true}. */
    private boolean enabled = true;

    /** Número máximo de peticiones permitidas por cliente dentro de la ventana. */
    private int capacity = 20;

    /** Duración de la ventana de conteo, en segundos. */
    private long windowSeconds = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
