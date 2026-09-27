package cl.antubank.notification.channel;

/**
 * Canal de entrega de notificaciones (tarea 7.2, Requisito 7, criterio 3).
 *
 * <p>Abstracción que desacopla <em>qué</em> se notifica (la {@link Notification} ya renderizada y
 * localizada) de <em>cómo</em> se entrega. Permite sustituir el canal (log, email, SMS, push) sin
 * tocar la lógica de consumo ni el renderizado del contenido.
 *
 * <p>En esta entrega la única implementación es un <strong>mock</strong> que registra en el log,
 * modelando un envío de email (ver {@link LoggingEmailNotificationChannel}).
 */
public interface NotificationChannel {

    /**
     * Entrega la notificación por este canal.
     *
     * @param notification notificación ya renderizada y localizada.
     */
    void send(Notification notification);
}
