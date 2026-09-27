package cl.antubank.ledger.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.ledger.AbstractPostgresIntegrationTest;
import cl.antubank.ledger.domain.EntryType;
import cl.antubank.ledger.persistence.LedgerEntryEntity;
import cl.antubank.ledger.persistence.LedgerEntryRepository;
import cl.antubank.ledger.persistence.LedgerTransactionEntity;
import cl.antubank.ledger.persistence.LedgerTransactionRepository;
import cl.antubank.ledger.service.AccountBalance;
import cl.antubank.ledger.service.BalanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test de integración del {@link TransferEventConsumer} contra un <strong>Kafka real</strong>
 * (Testcontainers) y un PostgreSQL real (tarea 5.3, Requisito 5, criterios 3 y 5; design.md,
 * secciones 5.2 y 6.2).
 *
 * <p>Escenarios:
 * <ul>
 *   <li><strong>Evento → asiento:</strong> un {@code TransferConfirmed} publicado al topic genera
 *       una transacción de doble entrada balanceada (débito origen / crédito destino) y los saldos
 *       derivados cuadran (origen −monto, destino +monto).</li>
 *   <li><strong>Idempotencia:</strong> el mismo evento entregado dos veces produce una única
 *       transacción; no se duplican asientos ni se desbalancean los saldos.</li>
 * </ul>
 */
@Testcontainers
class TransferEventConsumerKafkaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    LedgerTransactionRepository transactionRepository;

    @Autowired
    LedgerEntryRepository entryRepository;

    @Autowired
    BalanceService balanceService;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${ledger.events.transfer-topic}")
    String topic;

    @AfterEach
    void limpiar() {
        // Nota: los asientos son inmutables (triggers de V3 rechazan DELETE), por lo que no se
        // borran entre tests. Cada test usa cuentas y transferId únicos (UUID) para aislarse.
    }

    private KafkaTemplate<String, String> nuevoProductor() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private void publicarTransferConfirmed(
            KafkaTemplate<String, String> producer, TransferConfirmedEvent event) throws Exception {
        String payload = objectMapper.writeValueAsString(event);
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, event.transferId().toString(), payload);
        record.headers().add("eventType",
                TransferConfirmedEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion",
                Integer.toString(TransferConfirmedEvent.EVENT_VERSION)
                        .getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }

    private LedgerTransactionEntity buscarPorReferencia(UUID transferId) {
        return transactionRepository.findAll().stream()
                .filter(t -> transferId.toString().equals(t.getReference()))
                .findFirst()
                .orElse(null);
    }

    @Test
    void eventoDeTransferenciaGeneraAsientoBalanceadoYCuadraSaldos() throws Exception {
        UUID transferId = UUID.randomUUID();
        UUID origen = UUID.randomUUID();
        UUID destino = UUID.randomUUID();
        long montoMinor = 50_000L;

        TransferConfirmedEvent event = new TransferConfirmedEvent(
                transferId, origen, destino, montoMinor, Currency.CLP);

        KafkaTemplate<String, String> producer = nuevoProductor();
        try {
            publicarTransferConfirmed(producer, event);

            // El consumer asienta de forma asíncrona; esperar a que aparezca la transacción.
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> buscarPorReferencia(transferId) != null);
        } finally {
            producer.destroy();
        }

        // Se generó una única transacción de doble entrada con la referencia = transferId.
        LedgerTransactionEntity tx = buscarPorReferencia(transferId);
        assertThat(tx).isNotNull();
        assertThat(tx.getCurrency()).isEqualTo(Currency.CLP);
        assertThat(tx.getEntries()).hasSize(2);

        // El asiento está balanceado: Σ amount_minor con signo = 0 (débito + crédito).
        long neto = tx.getEntries().stream()
                .mapToLong(LedgerEntryEntity::getAmountMinor)
                .sum();
        assertThat(neto).isZero();

        // Débito a la cuenta origen, crédito a la cuenta destino.
        LedgerEntryEntity debito = tx.getEntries().stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst().orElseThrow();
        LedgerEntryEntity credito = tx.getEntries().stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .findFirst().orElseThrow();
        assertThat(debito.getAccountId()).isEqualTo(origen);
        assertThat(credito.getAccountId()).isEqualTo(destino);

        // Los saldos derivados cuadran (Requisito 5, criterio 5). Convención contable del ledger:
        // débito suma (+) y crédito resta (−). El asiento debita a la cuenta origen y acredita a la
        // destino, por lo que la origen queda +monto y la destino −monto; su suma es cero (cuadra).
        AccountBalance saldoOrigen = balanceService.balance(origen, Currency.CLP);
        AccountBalance saldoDestino = balanceService.balance(destino, Currency.CLP);
        assertThat(saldoOrigen.balance()).isEqualTo(Money.ofMinor(montoMinor, Currency.CLP));
        assertThat(saldoDestino.balance()).isEqualTo(Money.ofMinor(-montoMinor, Currency.CLP));
        // Los saldos de las dos cuentas involucradas suman cero: el asiento cuadra.
        assertThat(saldoOrigen.balance().plus(saldoDestino.balance()))
                .isEqualTo(Money.zero(Currency.CLP));
    }

    @Test
    void mismoEventoEntregadoDosVecesGeneraUnSoloAsiento() throws Exception {
        UUID transferId = UUID.randomUUID();
        UUID origen = UUID.randomUUID();
        UUID destino = UUID.randomUUID();
        long montoMinor = 30_000L;

        TransferConfirmedEvent event = new TransferConfirmedEvent(
                transferId, origen, destino, montoMinor, Currency.CLP);

        KafkaTemplate<String, String> producer = nuevoProductor();
        try {
            // Entrega duplicada del mismo evento (garantía "al menos una vez" de Kafka).
            publicarTransferConfirmed(producer, event);
            publicarTransferConfirmed(producer, event);

            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> buscarPorReferencia(transferId) != null);
        } finally {
            producer.destroy();
        }

        // Dar margen a que un eventual segundo procesamiento ocurra (y NO duplique).
        Thread.sleep(2_000);

        List<LedgerTransactionEntity> conReferencia = transactionRepository.findAll().stream()
                .filter(t -> transferId.toString().equals(t.getReference()))
                .toList();
        assertThat(conReferencia)
                .as("una re-entrega del mismo evento no debe duplicar el asiento")
                .hasSize(1);

        // Los saldos reflejan un único movimiento, no dos (crédito a destino: −monto una sola vez).
        AccountBalance saldoDestino = balanceService.balance(destino, Currency.CLP);
        assertThat(saldoDestino.balance()).isEqualTo(Money.ofMinor(-montoMinor, Currency.CLP));
    }
}
