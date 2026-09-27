package cl.antubank.notification.content;

import cl.antubank.notification.NotificationProperties;
import cl.antubank.notification.channel.Notification;
import cl.antubank.notification.messaging.TransferConfirmedEvent;
import cl.antubank.notification.messaging.TransferFlaggedEvent;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Renderiza el contenido localizado (asunto + cuerpo) de las notificaciones a partir de los eventos
 * notificables (tarea 7.2, Requisito 7, criterio 4).
 *
 * <p>El texto se resuelve desde plantillas de {@link MessageSource} ({@code messages_es.properties}
 * por defecto y {@code messages_en.properties}), interpolando los datos de la transferencia: monto
 * formateado según el locale (ver {@link MoneyFormatter}), cuenta y —para transferencias marcadas—
 * el motivo del riesgo, también localizado.
 *
 * <p><strong>Elección del locale.</strong> Como el origen es un evento de Kafka y no un request HTTP,
 * no hay cabecera {@code Accept-Language} de la que derivar el idioma. Se usa el locale por defecto
 * configurado ({@code notification.default-locale}, es-CL). Este método es el <em>hook</em> natural
 * para, en el futuro, resolver el idioma por preferencia del usuario a partir de los datos del evento.
 */
@Component
public class NotificationContentRenderer {

    private final MessageSource messages;
    private final Locale defaultLocale;

    public NotificationContentRenderer(MessageSource messages, NotificationProperties properties) {
        this.messages = messages;
        this.defaultLocale = properties.defaultLocale();
    }

    /**
     * Renderiza la notificación de una transferencia <strong>completada</strong>.
     *
     * @param event evento {@code TransferConfirmed}.
     * @return notificación lista para entregar (contenido en el locale por defecto).
     */
    public Notification renderTransferConfirmed(TransferConfirmedEvent event) {
        Locale locale = resolveLocale();
        String amount = MoneyFormatter.format(event.amountMinor(), event.currency(), locale);

        String subject = messages.getMessage(
                "notification.transfer.confirmed.subject", null, locale);
        String body = messages.getMessage(
                "notification.transfer.confirmed.body", new Object[] {amount}, locale);

        return new Notification(recipient(event.sourceAccountId()), subject, body, locale);
    }

    /**
     * Renderiza la notificación de una transferencia <strong>marcada como sospechosa</strong>.
     *
     * @param event evento {@code TransferFlagged}.
     * @return notificación lista para entregar (contenido en el locale por defecto).
     */
    public Notification renderTransferFlagged(TransferFlaggedEvent event) {
        Locale locale = resolveLocale();
        String amount = MoneyFormatter.format(event.amountMinor(), event.currency(), locale);
        String reason = messages.getMessage(
                "notification.reason." + event.reason().name(), null, locale);

        String subject = messages.getMessage(
                "notification.transfer.flagged.subject", null, locale);
        String body = messages.getMessage(
                "notification.transfer.flagged.body", new Object[] {amount, reason}, locale);

        return new Notification(recipient(event.sourceAccountId()), subject, body, locale);
    }

    /**
     * Determina el locale del contenido. Punto de extensión: hoy devuelve el locale por defecto
     * (es-CL); mañana podría derivarse de la preferencia del usuario asociada a la cuenta.
     */
    private Locale resolveLocale() {
        return defaultLocale;
    }

    private static String recipient(java.util.UUID sourceAccountId) {
        // En esta demo el destinatario es el titular de la cuenta origen; se identifica por su id.
        // En producción se resolvería a un email/teléfono desde el perfil del usuario.
        return "account:" + sourceAccountId;
    }
}
