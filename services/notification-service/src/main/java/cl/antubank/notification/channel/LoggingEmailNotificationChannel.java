package cl.antubank.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Canal de notificación <strong>mock</strong> que simula el envío de un email registrándolo en el
 * log (tarea 7.2, Requisito 7, criterio 3).
 *
 * <p>El envío es <strong>asíncrono</strong>: el método está anotado con {@link Async}, por lo que se
 * ejecuta en un hilo del pool de tareas de Spring y no bloquea al consumidor de Kafka que lo invoca.
 * Esto modela una entrega asíncrona real (un proveedor de email/SMS respondería con latencia) sin
 * depender de infraestructura externa.
 *
 * <p>En producción, esta clase se reemplazaría por una implementación de {@link NotificationChannel}
 * respaldada por un proveedor real (SES, SendGrid, etc.); el resto del servicio no cambia gracias a
 * la abstracción de canal.
 */
@Component
public class LoggingEmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailNotificationChannel.class);

    @Async
    @Override
    public void send(Notification notification) {
        // Mock: en lugar de conectar a un servidor SMTP/API, se registra el "email" en el log.
        log.info("[EMAIL mock] to={} locale={} subject=\"{}\" body=\"{}\"",
                notification.recipient(),
                notification.locale().toLanguageTag(),
                notification.subject(),
                notification.body());
    }
}
