package cl.antubank.fraud.rules;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración de las <strong>reglas de fraude</strong> (tarea 7.1, Requisito 7,
 * criterio 1). Todas admiten override por {@code application.yml} / variable de entorno.
 *
 * <p>Los umbrales están calibrados con valores <em>realistas para banca chilena</em> y expresados
 * en <strong>pesos chilenos (CLP)</strong>, que no usa decimales (minor units = pesos enteros).
 *
 * @param highAmount        regla de monto alto: umbral por transferencia individual.
 * @param velocity          regla de velocidad: frecuencia y monto acumulado por ventana de tiempo.
 */
@ConfigurationProperties(prefix = "fraud.rules")
public record FraudRulesProperties(HighAmount highAmount, Velocity velocity) {

    public FraudRulesProperties {
        if (highAmount == null) {
            throw new IllegalArgumentException("fraud.rules.high-amount es obligatorio");
        }
        if (velocity == null) {
            throw new IllegalArgumentException("fraud.rules.velocity es obligatorio");
        }
    }

    /**
     * Regla de <strong>monto alto</strong>: marca una transferencia individual cuyo monto
     * <em>supera</em> el umbral (comparación estricta: un monto igual al umbral NO se marca).
     *
     * @param thresholdClp umbral en pesos chilenos. Default 5.000.000 CLP: monto elevado pero
     *                     plausible para transferencias entre personas en Chile, por encima del
     *                     cual conviene una revisión de riesgo.
     */
    public record HighAmount(long thresholdClp) {
        public HighAmount {
            if (thresholdClp <= 0) {
                throw new IllegalArgumentException(
                        "fraud.rules.high-amount.threshold-clp debe ser positivo");
            }
        }
    }

    /**
     * Regla de <strong>velocidad</strong>: sobre una ventana de tiempo deslizante por cuenta origen,
     * marca cuando se <em>supera</em> el número máximo de transferencias o el monto acumulado máximo.
     *
     * @param window          duración de la ventana deslizante (default 1 minuto).
     * @param maxTransfers    número máximo de transferencias permitidas en la ventana; se marca al
     *                        superarlo (default 5: la 6.ª transferencia en el minuto se marca).
     * @param maxAmountClp    monto acumulado máximo permitido en la ventana, en CLP; se marca al
     *                        superarlo (default 10.000.000 CLP acumulados en el minuto).
     */
    public record Velocity(Duration window, int maxTransfers, long maxAmountClp) {
        public Velocity {
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException(
                        "fraud.rules.velocity.window debe ser una duración positiva");
            }
            if (maxTransfers <= 0) {
                throw new IllegalArgumentException(
                        "fraud.rules.velocity.max-transfers debe ser positivo");
            }
            if (maxAmountClp <= 0) {
                throw new IllegalArgumentException(
                        "fraud.rules.velocity.max-amount-clp debe ser positivo");
            }
        }
    }
}
