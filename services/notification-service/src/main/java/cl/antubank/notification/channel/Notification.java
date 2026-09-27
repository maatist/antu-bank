package cl.antubank.notification.channel;

import java.util.Locale;
import java.util.Objects;

/**
 * Notificación lista para entregar: destinatario, contenido ya renderizado (asunto + cuerpo) y el
 * idioma en que fue generado (tarea 7.2, Requisito 7, criterios 3 y 4).
 *
 * <p>Es una abstracción del <em>mensaje</em> independiente del canal: un {@link NotificationChannel}
 * decide cómo entregarla (por ahora, un canal mock que solo registra en el log). El contenido ya
 * viene localizado por el renderizador, de modo que el canal no necesita conocer {@code MessageSource}.
 *
 * @param recipient destinatario (identificador del titular; en esta demo, la cuenta origen).
 * @param subject   asunto ya localizado.
 * @param body      cuerpo ya localizado (con montos formateados en el locale correspondiente).
 * @param locale    idioma en que se generó el contenido (es / en); útil para trazabilidad y tests.
 */
public record Notification(String recipient, String subject, String body, Locale locale) {

    public Notification {
        Objects.requireNonNull(recipient, "recipient no puede ser null");
        Objects.requireNonNull(subject, "subject no puede ser null");
        Objects.requireNonNull(body, "body no puede ser null");
        Objects.requireNonNull(locale, "locale no puede ser null");
    }
}
