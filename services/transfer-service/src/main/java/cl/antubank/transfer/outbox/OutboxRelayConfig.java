package cl.antubank.transfer.outbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuración del <strong>Outbox Relay</strong> (tarea 5.2, Requisito 5).
 *
 * <p>Habilita el scheduling ({@link EnableScheduling}) para que el poller de {@link OutboxRelay}
 * se ejecute periódicamente, y liga las propiedades {@code outbox.relay.*} a
 * {@link OutboxRelayProperties}.
 *
 * <p>El {@code KafkaTemplate<String, String>} y el {@code ProducerFactory} los provee la
 * autoconfiguración de Spring Boot a partir de {@code spring.kafka.*} en {@code application.yml};
 * no es necesario declararlos aquí.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class OutboxRelayConfig {
}
