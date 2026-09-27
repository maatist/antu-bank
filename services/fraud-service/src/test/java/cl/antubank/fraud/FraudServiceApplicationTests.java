package cl.antubank.fraud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test del scaffold del fraud-service: verifica que el contexto de Spring arranca con la
 * mensajería cableada (consumer del topic de transferencias + producer del topic de fraude).
 *
 * <p>Usa un broker Kafka embebido para no depender de infraestructura externa: valida que el
 * {@code KafkaTemplate}, la fábrica de consumers, el motor de reglas y el publisher se cablean bien.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"transfer.events", "fraud.events"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class FraudServiceApplicationTests {

    @Test
    void contextLoads() {
        // Si el contexto carga, las reglas y la mensajería (consumer/producer) están cableadas.
    }
}
