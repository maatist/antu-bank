package cl.antubank.transfer.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.transfer.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de integración del {@link OutboxRelay} contra un <strong>Kafka real</strong> (Testcontainers)
 * y un PostgreSQL real, ejercitando el flujo completo del relay (tarea 5.2, Requisito 5,
 * criterios 2 y 4; design.md, sección 6.2).
 *
 * <p>Escenarios:
 * <ul>
 *   <li><strong>Publicación + marca:</strong> una fila de outbox pendiente se publica a Kafka y
 *       queda marcada como publicada; el mensaje llega al topic con la clave = {@code aggregateId},
 *       el payload JSON como valor y el esquema versionado en headers.</li>
 *   <li><strong>Robustez ante fallo:</strong> si el broker no está disponible (bootstrap-servers
 *       inválido), la publicación falla y la fila queda pendiente (sin marcar) para reintento.</li>
 * </ul>
 */
@Testcontainers
class OutboxRelayKafkaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    OutboxRelay relay;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${outbox.relay.topic}")
    String topic;

    @BeforeEach
    void limpiar() {
        outboxEventRepository.deleteAll();
    }

    @AfterEach
    void limpiarDespues() {
        outboxEventRepository.deleteAll();
    }

    private OutboxEventEntity nuevoPendiente(UUID aggregateId, String payload) {
        return new OutboxEventEntity(
                UUID.randomUUID(),
                TransferConfirmedEvent.AGGREGATE_TYPE,
                aggregateId,
                TransferConfirmedEvent.EVENT_TYPE,
                TransferConfirmedEvent.EVENT_VERSION,
                payload);
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

    @Test
    void relayPublicaEventoPendienteAKafkaYLoMarcaPublicado() throws Exception {
        UUID transferId = UUID.randomUUID();
        String payload = "{\"transferId\":\"" + transferId + "\",\"amountMinor\":50000,"
                + "\"currency\":\"CLP\"}";
        OutboxEventEntity saved = outboxEventRepository.save(nuevoPendiente(transferId, payload));

        int published = relay.publishPending();
        assertThat(published).isEqualTo(1);

        // El mensaje llegó a Kafka con clave = aggregateId, payload JSON y headers versionados.
        // Se busca el mensaje de ESTA transferencia por su clave, ya que el topic es compartido
        // entre escenarios y puede contener mensajes de otros tests de la misma JVM.
        try (KafkaConsumer<String, String> consumer = nuevoConsumidor()) {
            consumer.subscribe(List.of(topic));
            ConsumerRecord<String, String> record = pollForKey(consumer, transferId.toString());

            assertThat(record)
                    .as("se esperaba un mensaje con clave %s en el topic %s", transferId, topic)
                    .isNotNull();
            assertThat(record.key()).isEqualTo(transferId.toString());
            // El valor es el JSON del payload. Se compara semánticamente porque PostgreSQL, al
            // almacenar/leer JSONB, puede reordenar claves y reformatear espacios.
            assertThat(objectMapper.readTree(record.value()))
                    .isEqualTo(objectMapper.readTree(payload));

            Header type = record.headers().lastHeader(OutboxRelay.HEADER_EVENT_TYPE);
            Header version = record.headers().lastHeader(OutboxRelay.HEADER_EVENT_VERSION);
            assertThat(type).isNotNull();
            assertThat(version).isNotNull();
            assertThat(new String(type.value(), StandardCharsets.UTF_8))
                    .isEqualTo(TransferConfirmedEvent.EVENT_TYPE);
            assertThat(new String(version.value(), StandardCharsets.UTF_8))
                    .isEqualTo(Integer.toString(TransferConfirmedEvent.EVENT_VERSION));
        }

        // La fila quedó marcada como publicada (published = true, published_at fijado).
        OutboxEventEntity reloaded = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isPublished()).isTrue();
        assertThat(reloaded.getPublishedAt()).isNotNull();

        // Un segundo ciclo no reenvía el evento ya publicado.
        assertThat(relay.publishPending()).isZero();
    }

    @Test
    void falloAlPublicarDejaLaFilaPendienteParaReintento() {
        UUID transferId = UUID.randomUUID();
        OutboxEventEntity saved = outboxEventRepository.save(
                nuevoPendiente(transferId, "{\"transferId\":\"" + transferId + "\"}"));

        // Relay apuntando a un broker inexistente: la publicación síncrona fallará.
        OutboxRelayProperties badProps = new OutboxRelayProperties(topic, 1000L, 100);
        KafkaTemplate<String, String> brokenTemplate = new KafkaTemplate<>(
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(Map.of(
                        org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        "localhost:1",
                        org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringSerializer.class,
                        org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringSerializer.class,
                        org.apache.kafka.clients.producer.ProducerConfig.MAX_BLOCK_MS_CONFIG,
                        2000)));
        OutboxRelay brokenRelay =
                new OutboxRelay(outboxEventRepository, brokenTemplate, badProps);

        int published = brokenRelay.publishPending();

        assertThat(published).isZero();
        OutboxEventEntity reloaded = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isPublished())
                .as("un fallo de publicación no debe marcar la fila como publicada")
                .isFalse();
        assertThat(reloaded.getPublishedAt()).isNull();

        brokenTemplate.destroy();
    }

    /**
     * Consume del topic hasta encontrar un registro con la clave esperada o agotar el tiempo. El
     * topic es compartido, de modo que se ignoran mensajes de otras claves (otros tests).
     */
    private ConsumerRecord<String, String> pollForKey(
            KafkaConsumer<String, String> consumer, String expectedKey) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedKey.equals(record.key())) {
                    return record;
                }
            }
        }
        return null;
    }
}
