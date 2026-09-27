package cl.antubank.fraud.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.domain.money.Currency;
import cl.antubank.fraud.rules.FraudReason;
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
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de integración <strong>end-to-end</strong> del flujo de eventos del fraud-service contra un
 * <strong>Kafka real</strong> (Testcontainers), cerrando el ciclo completo que los tests unitarios
 * ejercitan por separado (tarea 7.3, Requisito 7, criterios 1 y 2; design.md, secciones 5.4 y 11).
 *
 * <p>El contexto de Spring arranca con el {@link TransferEventConsumer} suscrito al topic
 * {@code transfer.events} y el {@link FraudEventPublisher} publicando en {@code fraud.events}, ambos
 * apuntando al broker del contenedor vía {@link DynamicPropertySource}. El test actúa como
 * <em>transfer-service</em> (produciendo {@code TransferConfirmed} con el header versionado
 * {@code eventType}) y como consumidor aguas abajo de {@code fraud.events}, verificando que el
 * servicio reacciona realmente sobre la infraestructura de mensajería.
 *
 * <p>Escenarios:
 * <ul>
 *   <li><strong>Monto alto → marcado:</strong> un {@code TransferConfirmed} que supera el umbral de
 *       monto (CLP) produce un {@code TransferFlagged} en {@code fraud.events} con los campos y los
 *       headers versionados correctos.</li>
 *   <li><strong>Monto normal → sin evento:</strong> una transferencia bajo el umbral no genera
 *       ningún {@code TransferFlagged} (no hay falso positivo en el topic de fraude).</li>
 * </ul>
 *
 * <p>Como los topics pueden compartirse entre escenarios de la misma JVM, la aserción busca el
 * mensaje por su clave ({@code transferId}) mediante un helper de <em>poll</em>, siguiendo el patrón
 * de {@code OutboxRelayKafkaIntegrationTest} (transfer-service).
 */
@SpringBootTest
@Testcontainers
class FraudFlowKafkaIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    ObjectMapper objectMapper;

    @Value("${fraud.events.transfer-topic}")
    String transferTopic;

    @Value("${fraud.events.topic}")
    String fraudTopic;

    private KafkaTemplate<String, String> nuevoProductor() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
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
     * Publica un {@code TransferConfirmed} al topic de transferencias con la misma convención que el
     * outbox relay de transfer-service: clave = {@code transferId}, payload JSON y esquema versionado
     * en los headers {@code eventType} / {@code eventVersion}.
     */
    private void publicarTransferConfirmed(
            KafkaTemplate<String, String> producer, TransferConfirmedEvent event) throws Exception {
        String payload = objectMapper.writeValueAsString(event);
        ProducerRecord<String, String> record =
                new ProducerRecord<>(transferTopic, event.transferId().toString(), payload);
        record.headers().add("eventType",
                TransferConfirmedEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion",
                Integer.toString(TransferConfirmedEvent.EVENT_VERSION)
                        .getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }

    @Test
    void transferenciaDeMontoAltoProduceTransferFlaggedEnElTopicDeFraude() throws Exception {
        UUID transferId = UUID.randomUUID();
        UUID origen = UUID.randomUUID();
        UUID destino = UUID.randomUUID();
        // Supera el umbral por defecto de monto alto (5.000.000 CLP): debe marcarse.
        long montoMinor = 6_000_000L;

        TransferConfirmedEvent event = new TransferConfirmedEvent(
                transferId, origen, destino, montoMinor, Currency.CLP);

        KafkaTemplate<String, String> producer = nuevoProductor();
        try (KafkaConsumer<String, String> consumer = nuevoConsumidor()) {
            // Suscribirse ANTES de producir para no perder el evento resultante (offset earliest
            // igualmente lo cubre, pero suscribir antes reduce la latencia de la primera asignación).
            consumer.subscribe(List.of(fraudTopic));

            publicarTransferConfirmed(producer, event);

            ConsumerRecord<String, String> record = pollForKey(consumer, transferId.toString());
            assertThat(record)
                    .as("se esperaba un TransferFlagged con clave %s en %s", transferId, fraudTopic)
                    .isNotNull();

            // La clave preserva el orden por partición de una misma transferencia.
            assertThat(record.key()).isEqualTo(transferId.toString());

            // El payload deserializa al evento con los campos esperados (monto, moneda, motivo).
            TransferFlaggedEvent flagged =
                    objectMapper.readValue(record.value(), TransferFlaggedEvent.class);
            assertThat(flagged.transferId()).isEqualTo(transferId);
            assertThat(flagged.sourceAccountId()).isEqualTo(origen);
            assertThat(flagged.amountMinor()).isEqualTo(montoMinor);
            assertThat(flagged.currency()).isEqualTo(Currency.CLP);
            assertThat(flagged.reason()).isEqualTo(FraudReason.HIGH_AMOUNT);

            // El esquema versionado viaja en los headers (misma convención que transfer-service).
            Header type = record.headers().lastHeader(FraudEventPublisher.HEADER_EVENT_TYPE);
            Header version = record.headers().lastHeader(FraudEventPublisher.HEADER_EVENT_VERSION);
            assertThat(type).isNotNull();
            assertThat(version).isNotNull();
            assertThat(new String(type.value(), StandardCharsets.UTF_8))
                    .isEqualTo(TransferFlaggedEvent.EVENT_TYPE);
            assertThat(new String(version.value(), StandardCharsets.UTF_8))
                    .isEqualTo(Integer.toString(TransferFlaggedEvent.EVENT_VERSION));
        } finally {
            producer.destroy();
        }
    }

    @Test
    void transferenciaNormalNoProduceEventoDeFraude() throws Exception {
        UUID transferId = UUID.randomUUID();
        // Monto muy por debajo del umbral y sin velocidad: el motor no debe marcar nada.
        long montoMinor = 100_000L;

        TransferConfirmedEvent event = new TransferConfirmedEvent(
                transferId, UUID.randomUUID(), UUID.randomUUID(), montoMinor, Currency.CLP);

        KafkaTemplate<String, String> producer = nuevoProductor();
        try (KafkaConsumer<String, String> consumer = nuevoConsumidor()) {
            consumer.subscribe(List.of(fraudTopic));

            publicarTransferConfirmed(producer, event);

            // No debe aparecer ningún TransferFlagged con esta clave dentro de la ventana de espera.
            ConsumerRecord<String, String> record = pollForKey(consumer, transferId.toString());
            assertThat(record)
                    .as("una transferencia normal no debe producir TransferFlagged en %s", fraudTopic)
                    .isNull();
        } finally {
            producer.destroy();
        }
    }

    /**
     * Consume del topic hasta encontrar un registro con la clave esperada o agotar el tiempo. El
     * topic puede compartirse entre escenarios de la misma JVM, de modo que se ignoran mensajes de
     * otras claves. Devuelve {@code null} si no aparece dentro del plazo (usado también para
     * verificar la <em>ausencia</em> de un evento).
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
