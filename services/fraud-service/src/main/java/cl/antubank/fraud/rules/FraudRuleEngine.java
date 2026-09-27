package cl.antubank.fraud.rules;

import cl.antubank.domain.money.Currency;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Motor de <strong>reglas de fraude</strong> (tarea 7.1, Requisito 7, criterio 1).
 *
 * <p>Evalúa cada transferencia confirmada y decide si debe marcarse como sospechosa. Aplica, en
 * orden, dos familias de reglas con umbrales realistas en CLP (configurables vía
 * {@link FraudRulesProperties}):
 * <ol>
 *   <li><strong>Monto (single-tx):</strong> se marca si el monto de la transferencia
 *       <em>supera</em> el umbral de monto alto.</li>
 *   <li><strong>Velocidad (por cuenta origen):</strong> sobre una ventana deslizante en memoria por
 *       cuenta, se marca si —incluida la transferencia actual— se supera el número máximo de
 *       transferencias, o el monto acumulado máximo, dentro de la ventana.</li>
 * </ol>
 *
 * <p><strong>Alcance de moneda.</strong> Los umbrales se expresan en CLP (moneda principal), que no
 * usa decimales: sus minor units son pesos enteros, por lo que se comparan directamente con
 * {@code amountMinor}. Transferencias en otra moneda quedan fuera del alcance de estos umbrales CLP
 * y no se evalúan (Requisito 7 acota los umbrales a CLP); podrán cubrirse con reglas propias más
 * adelante.
 *
 * <p><strong>Concurrencia.</strong> La ventana por cuenta se guarda en un mapa concurrente y su
 * actualización se sincroniza por cuenta, de modo que el conteo/acumulado sea consistente aunque
 * el contenedor de Kafka procese particiones en paralelo.
 */
@Component
public class FraudRuleEngine {

    private final FraudRulesProperties properties;
    private final Clock clock;

    /** Ventanas deslizantes por cuenta origen (estado en memoria; servicio stateless en disco). */
    private final Map<UUID, AccountVelocityWindow> windows = new ConcurrentHashMap<>();

    public FraudRuleEngine(FraudRulesProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Evalúa una transferencia y devuelve el motivo por el que debe marcarse, si alguna regla se
     * dispara.
     *
     * @param sourceAccountId cuenta origen (clave de la ventana de velocidad).
     * @param amountMinor     monto de la transferencia en minor units.
     * @param currency        moneda del monto.
     * @return el {@link FraudReason} de la primera regla que se dispara, o vacío si ninguna aplica.
     */
    public Optional<FraudReason> evaluate(UUID sourceAccountId, long amountMinor, Currency currency) {
        // Los umbrales del Requisito 7 están definidos en CLP. Otras monedas no se evalúan aquí.
        if (currency != Currency.CLP) {
            return Optional.empty();
        }

        // Regla de monto alto (transacción individual): comparación estricta contra el umbral.
        if (amountMinor > properties.highAmount().thresholdClp()) {
            return Optional.of(FraudReason.HIGH_AMOUNT);
        }

        // Regla de velocidad: se registra la transferencia en la ventana de su cuenta y se evalúa.
        return evaluateVelocity(sourceAccountId, amountMinor);
    }

    private Optional<FraudReason> evaluateVelocity(UUID sourceAccountId, long amountMinor) {
        Instant now = Instant.now(clock);
        FraudRulesProperties.Velocity velocity = properties.velocity();

        AccountVelocityWindow window =
                windows.computeIfAbsent(sourceAccountId, k -> new AccountVelocityWindow());

        // Sincroniza por cuenta: expira lo antiguo, registra la actual y lee el estado consistente.
        synchronized (window) {
            window.expireOlderThan(now, velocity.window());
            window.record(now, amountMinor);

            int count = window.count();
            long total = window.totalAmountMinor();

            if (count > velocity.maxTransfers()) {
                return Optional.of(FraudReason.VELOCITY_COUNT);
            }
            if (total > velocity.maxAmountClp()) {
                return Optional.of(FraudReason.VELOCITY_AMOUNT);
            }
            return Optional.empty();
        }
    }
}
