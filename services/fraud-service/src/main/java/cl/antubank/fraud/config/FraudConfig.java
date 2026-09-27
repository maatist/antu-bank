package cl.antubank.fraud.config;

import cl.antubank.fraud.messaging.FraudEventProperties;
import cl.antubank.fraud.rules.FraudRulesProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del fraud-service (tarea 7.1, Requisito 7).
 *
 * <p>Liga las propiedades {@code fraud.rules.*} y {@code fraud.events.*} a sus records de
 * configuración y expone un {@link Clock} inyectable —usado por el motor de reglas para la ventana
 * de velocidad— que en tests puede sustituirse por un reloj fijo para controlar el tiempo.
 *
 * <p>El {@code KafkaTemplate} y las fábricas de consumer las provee la autoconfiguración de Spring
 * Boot a partir de {@code spring.kafka.*}; no se declaran aquí.
 */
@Configuration
@EnableConfigurationProperties({FraudRulesProperties.class, FraudEventProperties.class})
public class FraudConfig {

    /** Reloj del sistema (UTC) para fechar las transferencias en la ventana de velocidad. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
