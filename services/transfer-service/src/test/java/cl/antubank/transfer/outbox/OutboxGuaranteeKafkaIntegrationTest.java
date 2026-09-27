package cl.antubank.transfer.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.transfer.AbstractPostgresIntegrationTest;
import cl.antubank.transfer.api.CreateTransferRequest;
import cl.antubank.transfer.outbox.ConfigurableLedgerConfig.ConfigurableLedgerClient;
import cl.antubank.transfer.persistence.IdempotencyKeyRepository;
import cl.antubank.transfer.persistence.TransferRepository;
import cl.antubank.transfer.service.TransferResult;
import cl.antubank.transfer.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de integración que valida la <strong>garantía de publicación del Outbox pattern</strong>
 * de extremo a extremo del lado <em>productor</em> (tarea 5.4, Requisito 5, criterios 2 y 5;
 * design.md, sección 6.2 y sección 11).
 *
 * <p>A diferencia de {@link OutboxRelayKafkaIntegrationTest} —que siembra filas de outbox
 * directamente para ejercitar el relay de forma aislada— este test parte del <strong>flujo de
 * negocio real</strong>: invoca {@link TransferService#transfer} (que escribe la transferencia y
 * su evento en la outbox dentro de la misma transacción, Requisito 5, criterio 1) y luego drena la
 * outbox con el relay contra un <strong>Kafka real</strong> (Testcontainers). Así se comprueba que
 * un evento escrito por la operación de negocio <em>siempre termina publicado</em> y no se pierde.
 *
 * <p>Escenarios:
 * <ul>
 *   <li><strong>Garantía de publicación (evento de negocio → Kafka):</strong> una transferencia
 *       confirmada deja una fila pendiente en la outbox; al ejecutar el relay, el evento se publica
 *       al topic <em>exactamente una vez</em> y la fila queda marcada como publicada. Un segundo
 *       ciclo del relay <em>no</em> reenvía (no hay duplicados).</li>
 *   <li><strong>Drenado de múltiples pendientes en orden:</strong> varias transferencias generan
 *       varios eventos pendientes; el relay los drena todos en un ciclo y llegan a Kafka preservando
 *       el orden de ocurrencia (clave = {@code aggregateId} por partición).</li>
 * </ul>
 *
 * <p>Nota de alcance (per-side): una verdadera prueba cross-service requeriría ambas apps
 * corriendo. Aquí la garantía se valida por lado: este test cubre el lado productor (el relay
 * publica a Kafka lo que la operación de negocio escribió en la outbox), mientras que
 * {@code TransferEventConsumerKafkaIntegrationTest} en ledger-service cubre el lado consumidor
 * (el evento del topic genera el asiento de doble entrada balanceado).
 */
@Testcontainers
@Import(ConfigurableLedgerConfig.class)
class OutboxGuaranteeKafkaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    TransferService transferService;

    @Autowired
    OutboxRelay relay;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    ConfigurableLedgerClient ledger;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${outbox.relay.topic}")
    String topic;

    @BeforeEach
    void limpiar() {
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
        ledger.reset();
        ledger.setBalanceMinor(10_000_000L);
    }

    @AfterEach
    void limpiarDespues() {
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        transferRepository.deleteAll();
    }

    private CreateTransferRequest request(long amountMinor) {
        return new CreateTransferRequest(UUID.randomUUID(), UUID.randomUUID(), amountMinor);
    }

    private KafkaConsumer<String, String> nuevoConsumidor() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    /**
     * Drena el topic recolectando los registros cuya clave esté en {@code expectedKeys}, hasta
     * reunir {@code expected} de ellos o agotar el tiempo. El topic es compartido entre escenarios
     * de la misma JVM, así que se ignoran mensajes de otras claves.
     */
    private List<ConsumerRecord<String, String>> pollForKeys(
            KafkaConsumer<String, String> consumer, List<String> expectedKeys, int expected) {
        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline && collected.size() < expected) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedKeys.contains(record.key())) {
                    collected.add(record);
                }
            }
        }
        return collected;
    }

    @Test
    void eventoDeNegocioEscritoEnLaOutboxTerminaPublicadoEnKafkaExactamenteUnaVez() throws Exception {
        // (1) Operación de negocio real: la transferencia confirmada escribe el evento en la outbox
        // dentro de la misma transacción (Requisito 5, criterio 1). Aún NO se ha publicado a Kafka.
        TransferResult result = transferService.transfer("key-garantia-e2e", request(50_000));
        UUID transferId = result.transfer().getId();

        OutboxEventEntity pendiente = outboxEventRepository.findAll().get(0);
        assertThat(outboxEventRepository.findAll()).hasSize(1);
        assertThat(pendiente.getAggregateId()).isEqualTo(transferId);
        assertThat(pendiente.isPublished())
                .as("recién escrito por la operación de negocio, el evento aún no está publicado")
                .isFalse();

        // (2) El relay drena la outbox y publica a Kafka. La garantía: lo que la operación de
        // negocio escribió en la outbox termina publicado (Requisito 5, criterio 2).
        assertThat(relay.publishPending()).isEqualTo(1);

        // El evento llegó al topic con clave = aggregateId (transferId) y payload = JSON del evento.
        try (KafkaConsumer<String, String> consumer = nuevoConsumidor()) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> recibidos =
                    pollForKeys(consumer, List.of(transferId.toString()), 1);

            assertThat(recibidos)
                    .as("la operación de negocio debe terminar publicada exactamente una vez")
                    .hasSize(1);
            ConsumerRecord<String, String> record = recibidos.get(0);
            assertThat(record.key()).isEqualTo(transferId.toString());

            TransferConfirmedEvent payload =
                    objectMapper.readValue(record.value(), TransferConfirmedEvent.class);
            assertThat(payload.transferId()).isEqualTo(transferId);
            assertThat(payload.amountMinor()).isEqualTo(50_000L);
        }

        // (3) La fila quedó marcada como publicada, y un segundo ciclo del relay NO reenvía: sin
        // duplicados (la publicación es a-lo-más-una-vez desde el relay; el evento no se pierde ni
        // se republica una vez confirmado).
        OutboxEventEntity reloaded =
                outboxEventRepository.findById(pendiente.getId()).orElseThrow();
        assertThat(reloaded.isPublished()).isTrue();
        assertThat(reloaded.getPublishedAt()).isNotNull();
        assertThat(relay.publishPending())
                .as("un segundo ciclo no debe republicar un evento ya publicado")
                .isZero();
    }

    @Test
    void multiplesEventosPendientesSeDrenanEnUnCicloYNingunoSePierde() throws Exception {
        // Tres transferencias de negocio → tres eventos pendientes en la outbox, en orden de
        // ocurrencia. Cada una con su propia Idempotency-Key para no reproducir resultados.
        TransferResult t1 = transferService.transfer("key-orden-1", request(10_000));
        TransferResult t2 = transferService.transfer("key-orden-2", request(20_000));
        TransferResult t3 = transferService.transfer("key-orden-3", request(30_000));
        List<String> claves = List.of(
                t1.transfer().getId().toString(),
                t2.transfer().getId().toString(),
                t3.transfer().getId().toString());

        // El relay lee los pendientes ordenados por occurredAt ascendente: verifica el orden de
        // drenado en el origen (la garantía de orden global que puede ofrecer el relay). Kafka solo
        // preserva orden POR partición (clave = aggregateId), no un orden total entre agregados
        // distintos, de modo que la aserción de orden se hace aquí, sobre la fuente.
        List<OutboxEventEntity> pendientesEnOrden =
                outboxEventRepository.findByPublishedFalseOrderByOccurredAtAsc();
        assertThat(pendientesEnOrden).hasSize(3);
        assertThat(pendientesEnOrden.stream()
                        .map(e -> e.getAggregateId().toString())
                        .toList())
                .as("el relay drena los pendientes en orden de ocurrencia")
                .containsExactlyElementsOf(claves);

        // Un solo ciclo del relay drena los tres pendientes.
        assertThat(relay.publishPending()).isEqualTo(3);

        // Los tres eventos llegaron a Kafka: ninguno se pierde (garantía de publicación del outbox).
        // No se asume orden entre particiones; se comprueba que el conjunto completo esté presente.
        try (KafkaConsumer<String, String> consumer = nuevoConsumidor()) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> recibidos = pollForKeys(consumer, claves, 3);

            assertThat(recibidos).hasSize(3);
            assertThat(recibidos.stream().map(ConsumerRecord::key).toList())
                    .as("los tres eventos de negocio deben terminar publicados, sin pérdidas")
                    .containsExactlyInAnyOrderElementsOf(claves);
        }

        // No quedan pendientes y un segundo ciclo no republica (sin duplicados).
        assertThat(outboxEventRepository.findByPublishedFalseOrderByOccurredAtAsc()).isEmpty();
        assertThat(relay.publishPending()).isZero();
    }
}
