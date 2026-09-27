package cl.antubank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.transfer.AbstractPostgresIntegrationTest;
import cl.antubank.transfer.ledger.CountingLedgerConfig;
import cl.antubank.transfer.ledger.CountingLedgerConfig.CountingLedgerClient;
import cl.antubank.transfer.persistence.IdempotencyKeyRepository;
import cl.antubank.transfer.persistence.TransferRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Tests de <strong>concurrencia</strong> de la idempotencia de transferencias contra un PostgreSQL
 * real (Testcontainers), es decir, con la restricción única {@code uq_idempotency_key} a nivel de
 * base de datos que resuelve la carrera entre solicitudes simultáneas.
 *
 * <p>Cubren el Requisito 4, criterio 3: <em>cuando dos (o más) solicitudes concurrentes usan la
 * misma {@code Idempotency-Key}, el sistema debe garantizar que solo se aplique un movimiento</em>.
 * A diferencia de {@link TransferApiIntegrationTest} —que valida la reproducción idempotente de
 * forma secuencial (reintento)—, aquí se dispara una ráfaga de POST simultáneos con la misma clave
 * y se verifica que:
 * <ul>
 *   <li>se persiste exactamente <strong>una</strong> transferencia y una fila de idempotencia;</li>
 *   <li>todas las respuestas ({@code 201} para la ganadora, {@code 200} para las reproducidas)
 *       referencian el <strong>mismo</strong> id de transferencia;</li>
 *   <li>el ledger se invoca para registrar la transferencia <strong>a lo sumo una vez</strong>
 *       (sin doble asiento).</li>
 * </ul>
 *
 * <p>Se usa un doble del ledger que cuenta invocaciones de forma segura entre hilos
 * ({@link CountingLedgerConfig}), ya que el mock HTTP de los tests secuenciales no es apto para
 * uso concurrente.
 */
@Import(CountingLedgerConfig.class)
class TransferConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    /** Cantidad de solicitudes simultáneas por escenario. */
    private static final int CONCURRENT_REQUESTS = 16;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    CountingLedgerClient ledger;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository;

    /**
     * Amplía el pool de conexiones para que las {@value #CONCURRENT_REQUESTS} transacciones en
     * paralelo no se bloqueen esperando conexión (el pool por defecto de Hikari es 10).
     */
    @DynamicPropertySource
    static void poolProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size",
                () -> CONCURRENT_REQUESTS + 4);
    }

    @BeforeEach
    void limpiarEstado() {
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
        ledger.reset();
    }

    @AfterEach
    void limpiarDespues() {
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
    }

    private HttpEntity<Map<String, Object>> request(UUID source, UUID destination, long amountMinor,
                                                    String key) {
        Map<String, Object> body = Map.of(
                "sourceAccountId", source.toString(),
                "destinationAccountId", destination.toString(),
                "amountMinor", amountMinor);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(TransferController.IDEMPOTENCY_KEY_HEADER, key);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void solicitudesConcurrentesConMismaClaveAplicanUnSoloMovimiento() throws Exception {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 50_000;
        String key = "key-concurrente-" + UUID.randomUUID();
        HttpEntity<Map<String, Object>> request = request(source, destination, amount, key);

        // Barrera para maximizar la simultaneidad: todos los hilos parten a la vez.
        CyclicBarrier ready = new CyclicBarrier(CONCURRENT_REQUESTS);
        CountDownLatch done = new CountDownLatch(CONCURRENT_REQUESTS);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<Future<ResponseEntity<Map>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                Callable<ResponseEntity<Map>> task = () -> {
                    ready.await(10, TimeUnit.SECONDS); // sincroniza el arranque
                    try {
                        return rest.exchange("/transfers", HttpMethod.POST, request, Map.class);
                    } finally {
                        done.countDown();
                    }
                };
                futures.add(pool.submit(task));
            }

            assertThat(done.await(30, TimeUnit.SECONDS))
                    .as("todas las solicitudes concurrentes deben completar a tiempo")
                    .isTrue();

            // Recolectar respuestas y estados HTTP.
            List<ResponseEntity<Map>> responses = new java.util.ArrayList<>();
            for (Future<ResponseEntity<Map>> future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }

            // (1) Todas las respuestas son exitosas: exactamente una 201 (creada) y el resto 200
            // (reproducción de la misma transferencia), sin errores.
            long created = responses.stream()
                    .filter(r -> r.getStatusCode() == HttpStatus.CREATED)
                    .count();
            long replayed = responses.stream()
                    .filter(r -> r.getStatusCode() == HttpStatus.OK)
                    .count();
            assertThat(created)
                    .as("solo una solicitud debe crear la transferencia (201)")
                    .isEqualTo(1);
            assertThat(replayed)
                    .as("el resto debe reproducir el mismo resultado (200)")
                    .isEqualTo(CONCURRENT_REQUESTS - 1);

            // (2) Todas las respuestas referencian el mismo id de transferencia.
            List<Object> ids = responses.stream()
                    .map(r -> r.getBody().get("id"))
                    .distinct()
                    .toList();
            assertThat(ids)
                    .as("todas las respuestas deben apuntar a la misma transferencia")
                    .hasSize(1);
            assertThat(responses)
                    .allSatisfy(r -> assertThat(r.getBody().get("status")).isEqualTo("CONFIRMED"));

            // (3) En la base solo existe UNA transferencia y UNA clave de idempotencia.
            assertThat(transferRepository.count())
                    .as("un solo movimiento persistido (Requisito 4, criterio 3)")
                    .isEqualTo(1);
            assertThat(idempotencyKeyRepository.count())
                    .as("una sola fila de idempotencia para la clave")
                    .isEqualTo(1);
            UUID persistedId = transferRepository.findAll().get(0).getId();
            assertThat(ids.get(0))
                    .as("el id persistido coincide con el devuelto")
                    .isEqualTo(persistedId.toString());

            // (4) El ledger registró la transferencia a lo sumo una vez (sin doble asiento).
            assertThat(ledger.registerCallCount())
                    .as("el ledger no debe registrar la transferencia más de una vez")
                    .isEqualTo(1);
            assertThat(ledger.distinctRegisteredTransfers()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void clavesConcurrentesDistintasAplicanUnMovimientoCadaUna() throws Exception {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 12_000;

        // Cada hilo usa una clave distinta => cada solicitud es un movimiento independiente.
        CyclicBarrier ready = new CyclicBarrier(CONCURRENT_REQUESTS);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<Future<ResponseEntity<Map>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                String key = "key-distinta-" + i + "-" + UUID.randomUUID();
                HttpEntity<Map<String, Object>> request =
                        request(source, destination, amount, key);
                Callable<ResponseEntity<Map>> task = () -> {
                    ready.await(10, TimeUnit.SECONDS);
                    return rest.exchange("/transfers", HttpMethod.POST, request, Map.class);
                };
                futures.add(pool.submit(task));
            }

            List<ResponseEntity<Map>> responses = new java.util.ArrayList<>();
            for (Future<ResponseEntity<Map>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            // Todas crean (201) una transferencia distinta.
            assertThat(responses)
                    .allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED));
            long distinctIds = responses.stream().map(r -> r.getBody().get("id")).distinct().count();
            assertThat(distinctIds)
                    .as("cada clave produce una transferencia distinta")
                    .isEqualTo(CONCURRENT_REQUESTS);

            // Persistencia y ledger: un movimiento por clave.
            assertThat(transferRepository.count()).isEqualTo(CONCURRENT_REQUESTS);
            assertThat(idempotencyKeyRepository.count()).isEqualTo(CONCURRENT_REQUESTS);
            assertThat(ledger.registerCallCount()).isEqualTo(CONCURRENT_REQUESTS);
            assertThat(ledger.distinctRegisteredTransfers()).isEqualTo(CONCURRENT_REQUESTS);
        } finally {
            pool.shutdownNow();
        }
    }
}
