package cl.antubank.notification;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración del notification-service (tarea 7.2, Requisito 7, criterios 3 y 4).
 *
 * @param defaultLocale locale por defecto del contenido de las notificaciones. Como no hay request
 *                      HTTP (ni cabecera {@code Accept-Language}) desde el que derivar el idioma, se
 *                      usa este valor; por defecto {@code es-CL} (banca chilena). Es el punto de
 *                      extensión para, más adelante, resolver el idioma por preferencia del usuario.
 * @param events        topics de los eventos notificables.
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(Locale defaultLocale, Events events) {

    public NotificationProperties {
        if (defaultLocale == null) {
            defaultLocale = Locale.forLanguageTag("es-CL");
        }
    }

    /**
     * Topics de origen de los eventos notificables.
     *
     * @param transferTopic topic de eventos de transferencia (evento {@code TransferConfirmed}).
     * @param fraudTopic    topic de eventos de fraude (evento {@code TransferFlagged}).
     */
    public record Events(String transferTopic, String fraudTopic) {

        public Events {
            if (transferTopic == null || transferTopic.isBlank()) {
                throw new IllegalArgumentException("notification.events.transfer-topic no puede estar vacío");
            }
            if (fraudTopic == null || fraudTopic.isBlank()) {
                throw new IllegalArgumentException("notification.events.fraud-topic no puede estar vacío");
            }
        }
    }
}
