package cl.antubank.notification.config;

import cl.antubank.notification.NotificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del notification-service (tarea 7.2, Requisito 7).
 *
 * <p>Liga las propiedades {@code notification.*} a {@link NotificationProperties}. El
 * {@code MessageSource} (contenido i18n) lo autoconfigura Spring Boot a partir de
 * {@code spring.messages.*}, y las fábricas de consumer de Kafka a partir de {@code spring.kafka.*};
 * no se declaran aquí. El pool de tareas para {@code @Async} lo provee la autoconfiguración de Spring
 * Boot (habilitado con {@code @EnableAsync} en la clase de aplicación).
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {
}
