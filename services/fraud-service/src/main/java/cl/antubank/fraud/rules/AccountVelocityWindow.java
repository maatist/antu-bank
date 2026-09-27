package cl.antubank.fraud.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ventana deslizante <strong>en memoria</strong> de las transferencias recientes de una cuenta
 * origen, para evaluar la regla de velocidad (tarea 7.1, Requisito 7, criterio 1).
 *
 * <p>Guarda, por cada transferencia dentro de la ventana, su instante y su monto (minor units).
 * Al registrar una nueva transferencia primero <em>expira</em> las que quedaron fuera de la ventana
 * (más antiguas que {@code ahora - window}), de modo que el conteo y el monto acumulado reflejan
 * solo el período vigente.
 *
 * <p><strong>No es thread-safe.</strong> El acceso concurrente se serializa externamente en
 * {@link FraudRuleEngine} (sincronización por cuenta), consistente con un consumer Kafka que procesa
 * eventos de una misma partición de forma secuencial.
 */
final class AccountVelocityWindow {

    /** Una entrada de la ventana: instante de la transferencia y su monto en minor units. */
    private record Entry(Instant at, long amountMinor) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    /** Suma incremental de los montos vigentes en la ventana (evita recorrer para acumular). */
    private long totalAmountMinor;

    /**
     * Descarta las entradas anteriores al inicio de la ventana ({@code now - window}).
     *
     * @param now    instante de referencia.
     * @param window duración de la ventana.
     */
    void expireOlderThan(Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        // Las entradas están ordenadas por inserción (tiempo creciente): expiran desde el frente.
        while (!entries.isEmpty() && entries.peekFirst().at().isBefore(cutoff)) {
            totalAmountMinor -= entries.pollFirst().amountMinor();
        }
    }

    /**
     * Registra una transferencia en la ventana.
     *
     * @param now         instante de la transferencia.
     * @param amountMinor monto en minor units.
     */
    void record(Instant now, long amountMinor) {
        entries.addLast(new Entry(now, amountMinor));
        totalAmountMinor += amountMinor;
    }

    /** @return número de transferencias vigentes en la ventana. */
    int count() {
        return entries.size();
    }

    /** @return monto acumulado (minor units) de las transferencias vigentes en la ventana. */
    long totalAmountMinor() {
        return totalAmountMinor;
    }

    /** @return {@code true} si no quedan transferencias vigentes (la ventana puede podarse). */
    boolean isEmpty() {
        return entries.isEmpty();
    }
}
