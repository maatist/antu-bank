package cl.antubank.fraud.rules;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.domain.money.Currency;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios del motor de reglas de fraude (tarea 7.1, Requisito 7, criterio 1).
 *
 * <p>Cubren los límites de la regla de monto alto (comparación estricta) y de la regla de velocidad
 * (frecuencia y monto acumulado dentro de la ventana, y expiración al salir de ella). Se usa un
 * {@link MutableClock} para controlar el tiempo sin dormir el test.
 */
class FraudRuleEngineTest {

    private static final long HIGH_AMOUNT_THRESHOLD = 5_000_000L;
    private static final int MAX_TRANSFERS = 5;
    private static final long MAX_AMOUNT = 10_000_000L;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** Reloj mutable para avanzar el tiempo de forma determinista en la ventana de velocidad. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private FraudRuleEngine engineWith(MutableClock clock) {
        FraudRulesProperties props = new FraudRulesProperties(
                new FraudRulesProperties.HighAmount(HIGH_AMOUNT_THRESHOLD),
                new FraudRulesProperties.Velocity(WINDOW, MAX_TRANSFERS, MAX_AMOUNT));
        return new FraudRuleEngine(props, clock);
    }

    // --- Regla de monto alto -------------------------------------------------------------------

    @Test
    void montoIgualAlUmbralNoSeMarca() {
        FraudRuleEngine engine = engineWith(new MutableClock(Instant.parse("2024-01-01T00:00:00Z")));

        Optional<FraudReason> reason =
                engine.evaluate(UUID.randomUUID(), HIGH_AMOUNT_THRESHOLD, Currency.CLP);

        assertThat(reason).isEmpty();
    }

    @Test
    void montoUnPesoSobreElUmbralSeMarcaPorHighAmount() {
        FraudRuleEngine engine = engineWith(new MutableClock(Instant.parse("2024-01-01T00:00:00Z")));

        Optional<FraudReason> reason =
                engine.evaluate(UUID.randomUUID(), HIGH_AMOUNT_THRESHOLD + 1, Currency.CLP);

        assertThat(reason).contains(FraudReason.HIGH_AMOUNT);
    }

    @Test
    void montoBajoElUmbralNoSeMarca() {
        FraudRuleEngine engine = engineWith(new MutableClock(Instant.parse("2024-01-01T00:00:00Z")));

        Optional<FraudReason> reason =
                engine.evaluate(UUID.randomUUID(), 100_000L, Currency.CLP);

        assertThat(reason).isEmpty();
    }

    @Test
    void monedaDistintaDeClpNoSeEvalua() {
        FraudRuleEngine engine = engineWith(new MutableClock(Instant.parse("2024-01-01T00:00:00Z")));

        // Un monto que en CLP se marcaría por monto alto, en USD queda fuera de alcance.
        Optional<FraudReason> reason =
                engine.evaluate(UUID.randomUUID(), HIGH_AMOUNT_THRESHOLD + 1, Currency.USD);

        assertThat(reason).isEmpty();
    }

    // --- Regla de velocidad: frecuencia --------------------------------------------------------

    @Test
    void hastaElMaximoDeTransferenciasEnLaVentanaNoSeMarca() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        FraudRuleEngine engine = engineWith(clock);
        UUID account = UUID.randomUUID();

        // Las primeras MAX_TRANSFERS transferencias pequeñas no disparan la regla.
        for (int i = 0; i < MAX_TRANSFERS; i++) {
            assertThat(engine.evaluate(account, 1_000L, Currency.CLP)).isEmpty();
            clock.advance(Duration.ofSeconds(1));
        }
    }

    @Test
    void superarElMaximoDeTransferenciasEnLaVentanaSeMarcaPorVelocityCount() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        FraudRuleEngine engine = engineWith(clock);
        UUID account = UUID.randomUUID();

        for (int i = 0; i < MAX_TRANSFERS; i++) {
            assertThat(engine.evaluate(account, 1_000L, Currency.CLP)).isEmpty();
            clock.advance(Duration.ofSeconds(1));
        }

        // La transferencia MAX_TRANSFERS+1, aún dentro del minuto, supera el conteo.
        assertThat(engine.evaluate(account, 1_000L, Currency.CLP))
                .contains(FraudReason.VELOCITY_COUNT);
    }

    @Test
    void transferenciasQueSalenDeLaVentanaExpiranYNoSeMarca() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        FraudRuleEngine engine = engineWith(clock);
        UUID account = UUID.randomUUID();

        // MAX_TRANSFERS transferencias en el primer instante.
        for (int i = 0; i < MAX_TRANSFERS; i++) {
            assertThat(engine.evaluate(account, 1_000L, Currency.CLP)).isEmpty();
        }

        // Se avanza más allá de la ventana: las anteriores expiran y el conteo se reinicia.
        clock.advance(WINDOW.plusSeconds(1));

        assertThat(engine.evaluate(account, 1_000L, Currency.CLP)).isEmpty();
    }

    @Test
    void laVentanaEsPorCuenta() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        FraudRuleEngine engine = engineWith(clock);
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        // La cuenta A satura su ventana.
        for (int i = 0; i < MAX_TRANSFERS + 1; i++) {
            engine.evaluate(accountA, 1_000L, Currency.CLP);
        }

        // La cuenta B no se ve afectada por la actividad de A.
        assertThat(engine.evaluate(accountB, 1_000L, Currency.CLP)).isEmpty();
    }

    // --- Regla de velocidad: monto acumulado ---------------------------------------------------

    @Test
    void superarElMontoAcumuladoEnLaVentanaSeMarcaPorVelocityAmount() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        FraudRuleEngine engine = engineWith(clock);
        UUID account = UUID.randomUUID();

        // Dos transferencias por debajo del umbral de monto alto individual, pero cuya suma
        // (dentro de la ventana) supera el monto acumulado máximo: 4M + 4M + 4M = 12M > 10M.
        long each = 4_000_000L; // < HIGH_AMOUNT_THRESHOLD, no dispara la regla de monto.
        assertThat(engine.evaluate(account, each, Currency.CLP)).isEmpty();
        clock.advance(Duration.ofSeconds(1));
        assertThat(engine.evaluate(account, each, Currency.CLP)).isEmpty();
        clock.advance(Duration.ofSeconds(1));

        assertThat(engine.evaluate(account, each, Currency.CLP))
                .contains(FraudReason.VELOCITY_AMOUNT);
    }
}
