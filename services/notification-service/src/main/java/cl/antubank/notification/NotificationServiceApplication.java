package cl.antubank.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Punto de entrada del notification-service de Antu Bank.
 *
 * <p>Consume <strong>eventos notificables</strong> desde Kafka y envía notificaciones al usuario de
 * forma <strong>asíncrona</strong> mediante un canal mock (log/email) — ver requirements.md,
 * Requisito 7, criterios 3 y 4; design.md, secciones 5.4 y 8. Los eventos notificables son:
 * <ul>
 *   <li>{@code TransferConfirmed} (topic {@code transfer.events}, publicado por transfer-service):
 *       avisa al usuario que su transferencia se completó.</li>
 *   <li>{@code TransferFlagged} (topic {@code fraud.events}, publicado por fraud-service en la
 *       tarea 7.1): avisa sobre una transferencia sospechosa.</li>
 * </ul>
 *
 * <p>Es un servicio <strong>stateless</strong>: no persiste en base de datos. El contenido de las
 * notificaciones (asunto/cuerpo) se resuelve vía {@code MessageSource} en español (por defecto,
 * es-CL) o inglés.
 *
 * <p>{@link EnableAsync} habilita el envío asíncrono: el listener de Kafka delega el envío a un
 * método {@code @Async}, de modo que el hilo consumidor no se bloquea entregando la notificación.
 */
@SpringBootApplication
@EnableAsync
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
