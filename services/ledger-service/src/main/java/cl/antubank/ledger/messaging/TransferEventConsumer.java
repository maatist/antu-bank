package cl.antubank.ledger.messaging;

import cl.antubank.ledger.service.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumer de Kafka que asienta en el ledger las transferencias confirmadas por transfer-service
 * (tarea 5.3, Requisito 5, criterios 3 y 5; design.md, secciones 5.2 y 6.2).
 *
 * <p><strong>Flujo.</strong> El relay de la outbox de transfer-service publica cada evento en el
 * topic {@code transfer.events} con el payload JSON como valor y el esquema versionado en los
 * headers {@code eventType} / {@code eventVersion}. Este listener:
 * <ol>
 *   <li>Filtra por el header {@code eventType}: solo procesa {@code TransferConfirmed} (otros tipos
 *       que compartan el topic se ignoran sin fallar).</li>
 *   <li>Deserializa el payload a {@link TransferConfirmedEvent}.</li>
 *   <li>Delega en {@link LedgerService#settleTransfer} la generación del asiento de doble entrada
 *       (débito origen / crédito destino), usando el {@code transferId} como referencia idempotente.</li>
 * </ol>
 *
 * <p><strong>Idempotencia (entrega "al menos una vez").</strong> Kafka puede reentregar un mismo
 * evento (p. ej. si el commit del offset falla tras procesar). La deduplicación por
 * {@code reference = transferId} en {@link LedgerService} garantiza que una re-entrega no cree
 * asientos duplicados: el segundo procesamiento no genera transacción y los saldos siguen cuadrando
 * (Requisito 5, criterio 5).
 */
@Component
public class TransferEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public TransferEventConsumer(LedgerService ledgerService, ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    /**
     * Escucha el topic de eventos de transferencia y asienta las confirmaciones.
     *
     * <p>El {@code group-id} y el topic se resuelven desde {@code application.yml}
     * ({@code spring.kafka.consumer.group-id} y {@code ledger.events.transfer-topic}). El header
     * {@code eventType} llega como {@code byte[]} (lo escribe el relay como UTF-8); se decodifica y
     * se usa para enrutar. Un payload que no corresponda a {@code TransferConfirmed} se descarta.
     *
     * @param payload   valor del mensaje: JSON del evento.
     * @param eventType header con el tipo de evento (puede ser {@code null} en mensajes legados).
     * @param key       clave del mensaje (aggregateId = transferId), solo para trazas.
     */
    @KafkaListener(
            topics = "${ledger.events.transfer-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onTransferEvent(
            @Payload String payload,
            @Header(name = "eventType", required = false) byte[] eventType,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {

        String type = eventType == null ? null : new String(eventType, StandardCharsets.UTF_8);
        if (!TransferConfirmedEvent.EVENT_TYPE.equals(type)) {
            log.debug("Evento ignorado (eventType={}, key={}): no es TransferConfirmed", type, key);
            return;
        }

        TransferConfirmedEvent event = deserialize(payload);
        ledgerService.settleTransfer(
                        event.transferId(),
                        event.sourceAccountId(),
                        event.destinationAccountId(),
                        event.amountMinor(),
                        event.currency())
                .ifPresentOrElse(
                        tx -> log.info("Asiento generado para la transferencia {} (ref={})",
                                event.transferId(), tx.reference()),
                        () -> log.debug("Transferencia {} ya asentada; evento duplicado ignorado",
                                event.transferId()));
    }

    private TransferConfirmedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, TransferConfirmedEvent.class);
        } catch (Exception e) {
            // Un payload ilegible es un error de contrato: se propaga para no confirmar el offset
            // silenciosamente ni asentar datos corruptos.
            throw new IllegalStateException(
                    "No se pudo deserializar el evento TransferConfirmed", e);
        }
    }
}
