package cl.antubank.transfer.ledger;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Configuración de test que sustituye el ledger-service real por un doble
 * <strong>thread-safe</strong> que <em>cuenta</em> las invocaciones.
 *
 * <p>A diferencia de {@link MockLedgerConfig} (basado en {@link
 * org.springframework.test.web.client.MockRestServiceServer}, que no es seguro para uso
 * concurrente ni tolera solicitudes en paralelo), este doble está pensado para los tests de
 * concurrencia de la tarea 4.4: varios hilos disparan {@code POST /transfers} con la misma
 * {@code Idempotency-Key} y se debe garantizar que el ledger se invoque a lo sumo una vez
 * (Requisito 4, criterio 3: doble envío = un solo movimiento).
 *
 * <p>El doble reporta siempre saldo suficiente (para que la validación de fondos no interfiera con
 * lo que se está midiendo) y registra, mediante contadores atómicos, cuántas veces se consultó el
 * saldo y cuántas se registró una transferencia, junto con los ids de transferencia registrados.
 */
@TestConfiguration
public class CountingLedgerConfig {

    /** Doble del {@link LedgerClient} que cuenta invocaciones de forma segura entre hilos. */
    public static final class CountingLedgerClient extends LedgerClient {

        private final long balanceMinor;
        private final AtomicInteger balanceCalls = new AtomicInteger();
        private final AtomicInteger registerCalls = new AtomicInteger();
        // Ids de transferencia efectivamente registrados en el ledger (para detectar duplicados).
        private final ConcurrentHashMap<UUID, Boolean> registeredTransferIds = new ConcurrentHashMap<>();

        CountingLedgerClient(long balanceMinor) {
            // No se usa transporte HTTP real: sobrescribimos ambas operaciones.
            super(null);
            this.balanceMinor = balanceMinor;
        }

        @Override
        public Money balance(UUID accountId, Currency currency) {
            balanceCalls.incrementAndGet();
            return Money.ofMinor(balanceMinor, currency);
        }

        @Override
        public void registerTransfer(UUID transferId, UUID source, UUID destination, Money amount) {
            registerCalls.incrementAndGet();
            registeredTransferIds.put(transferId, Boolean.TRUE);
        }

        /** Cantidad de veces que se consultó el saldo al ledger. */
        public int balanceCallCount() {
            return balanceCalls.get();
        }

        /** Cantidad de veces que se registró una transferencia (doble entrada) en el ledger. */
        public int registerCallCount() {
            return registerCalls.get();
        }

        /** Cantidad de transferencias distintas efectivamente registradas en el ledger. */
        public int distinctRegisteredTransfers() {
            return registeredTransferIds.size();
        }

        /** Reinicia los contadores entre escenarios de test. */
        public void reset() {
            balanceCalls.set(0);
            registerCalls.set(0);
            registeredTransferIds.clear();
        }
    }

    /**
     * Cliente del ledger primario para tests de concurrencia. Reporta un saldo holgado y cuenta
     * las invocaciones de forma atómica.
     */
    @Bean
    @Primary
    public CountingLedgerClient countingLedgerClient() {
        // Saldo amplio: la validación de fondos nunca rechaza en estos escenarios.
        return new CountingLedgerClient(1_000_000_000L);
    }
}
