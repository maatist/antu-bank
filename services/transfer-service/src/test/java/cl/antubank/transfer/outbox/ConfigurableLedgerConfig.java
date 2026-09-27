package cl.antubank.transfer.outbox;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.transfer.ledger.LedgerClient;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Doble configurable del {@link LedgerClient} para los tests de outbox (tarea 5.1).
 *
 * <p>Permite fijar el saldo que reporta {@link LedgerClient#balance} y, opcionalmente, forzar que
 * el registro de la doble entrada falle, para verificar que la escritura en la outbox comparte la
 * transacción de negocio (si la transacción se revierte, la fila de outbox tampoco queda escrita).
 */
@TestConfiguration
public class ConfigurableLedgerConfig {

    /** Doble del ledger cuyo comportamiento se ajusta desde cada escenario de test. */
    public static final class ConfigurableLedgerClient extends LedgerClient {

        private final AtomicLong balanceMinor = new AtomicLong(1_000_000_000L);
        private volatile boolean failOnRegister = false;
        private final AtomicInteger registerCalls = new AtomicInteger();
        private final AtomicInteger reverseCalls = new AtomicInteger();
        // Efecto neto acumulado sobre el saldo del origen: -monto al asentar, +monto al revertir.
        // Permite verificar que, tras compensar, el efecto neto es cero (Requisito 6, criterio 3).
        private final AtomicLong netSourceEffectMinor = new AtomicLong(0);

        public ConfigurableLedgerClient() {
            super(null);
        }

        @Override
        public Money balance(UUID accountId, Currency currency) {
            return Money.ofMinor(balanceMinor.get(), currency);
        }

        @Override
        public void registerTransfer(UUID transferId, UUID source, UUID destination, Money amount) {
            registerCalls.incrementAndGet();
            if (failOnRegister) {
                throw new IllegalStateException("Fallo simulado del ledger al registrar el asiento");
            }
            // El asiento debita el origen: efecto -monto sobre su saldo.
            netSourceEffectMinor.addAndGet(-amount.toMinorUnits());
        }

        @Override
        public void reverseTransfer(UUID transferId, UUID source, UUID destination, Money amount) {
            reverseCalls.incrementAndGet();
            // La reversa acredita el origen: efecto +monto, que cancela el débito del asiento.
            netSourceEffectMinor.addAndGet(amount.toMinorUnits());
        }

        /** Fija el saldo (minor units) que reportará la consulta de fondos. */
        public void setBalanceMinor(long value) {
            balanceMinor.set(value);
        }

        /** Configura si el registro de la doble entrada debe lanzar una excepción. */
        public void setFailOnRegister(boolean value) {
            this.failOnRegister = value;
        }

        public int registerCallCount() {
            return registerCalls.get();
        }

        /** Número de asientos inversos (reversas) registrados como compensación. */
        public int reverseCallCount() {
            return reverseCalls.get();
        }

        /**
         * Efecto neto acumulado sobre el saldo del origen: cero significa que el asiento y su reversa
         * se cancelan (saldos íntegros tras compensar; Requisito 6, criterio 3).
         */
        public long netSourceEffectMinor() {
            return netSourceEffectMinor.get();
        }

        /** Restaura el estado por defecto entre escenarios. */
        public void reset() {
            balanceMinor.set(1_000_000_000L);
            failOnRegister = false;
            registerCalls.set(0);
            reverseCalls.set(0);
            netSourceEffectMinor.set(0);
        }
    }

    @Bean
    @Primary
    public ConfigurableLedgerClient configurableLedgerClient() {
        return new ConfigurableLedgerClient();
    }
}
