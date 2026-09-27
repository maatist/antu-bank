package cl.antubank.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del fraud-service de Antu Bank.
 *
 * <p>Detecta transferencias sospechosas en tiempo casi real aplicando <strong>reglas de monto y de
 * velocidad</strong> con umbrales realistas en CLP (ver requirements.md, Requisito 7; design.md,
 * sección 5.4). Consume el evento {@code TransferConfirmed} publicado por transfer-service en el
 * topic {@code transfer.events} y, cuando una regla se dispara, emite un evento
 * {@code TransferFlagged} a Kafka.
 *
 * <p>Es un servicio <strong>stateless</strong>: no persiste en base de datos. La regla de velocidad
 * mantiene una ventana deslizante <em>en memoria</em> por cuenta origen (aceptable para un motor de
 * fraude de demostración; en producción se externalizaría a un store de baja latencia).
 */
@SpringBootApplication
public class FraudServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudServiceApplication.class, args);
    }
}
