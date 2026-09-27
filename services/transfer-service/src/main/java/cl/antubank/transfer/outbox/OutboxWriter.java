package cl.antubank.transfer.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Escribe eventos de dominio en la tabla {@code outbox} (Outbox pattern, Requisito 5, criterio 1).
 *
 * <p><strong>Transaccionalidad.</strong> Este componente NO abre ni marca una transacción propia:
 * está pensado para invocarse <em>dentro</em> de la transacción de negocio que persiste la
 * transferencia. Al usar la propagación por defecto ({@code REQUIRED}), la inserción en la outbox
 * se une a esa transacción, garantizando atomicidad: el evento se persiste si y solo si la
 * transferencia también lo hace (y viceversa). Si la transacción de negocio se revierte (por
 * ejemplo, por fondos insuficientes o por una colisión de idempotencia), la fila de outbox tampoco
 * queda escrita.
 *
 * <p>La serialización del payload a JSON usa el {@link ObjectMapper} de Spring (configurado por
 * Spring Boot), y el tipo/versión del evento se guardan versionados en columnas dedicadas
 * (Requisito 5, criterio 4).
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persiste un evento {@link TransferConfirmedEvent} en la outbox, en la transacción activa del
     * llamador.
     *
     * @param event el evento de transferencia confirmada a registrar.
     * @return la fila de outbox persistida.
     * @throws IllegalStateException si el payload no puede serializarse a JSON (no debería ocurrir
     *                               con un record de campos simples; se trata como error de programación).
     */
    public OutboxEventEntity appendTransferConfirmed(TransferConfirmedEvent event) {
        String payload = serialize(event);
        OutboxEventEntity row = new OutboxEventEntity(
                UUID.randomUUID(),
                TransferConfirmedEvent.AGGREGATE_TYPE,
                event.transferId(),
                TransferConfirmedEvent.EVENT_TYPE,
                TransferConfirmedEvent.EVENT_VERSION,
                payload);
        return outboxEventRepository.save(row);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "No se pudo serializar el payload del evento de outbox", e);
        }
    }
}
