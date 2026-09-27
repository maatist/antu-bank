package cl.antubank.transfer.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Tests unitarios de la lógica del {@link OutboxRelay} (tarea 5.2, Requisito 5) con un
 * {@link KafkaTemplate} y un {@link OutboxEventRepository} simulados con Mockito.
 *
 * <p>Verifican, sin infraestructura:
 * <ul>
 *   <li>que cada evento pendiente se publica al topic configurado usando el {@code aggregateId}
 *       como clave y el payload JSON como valor, con el esquema versionado en headers (criterios
 *       2 y 4);</li>
 *   <li>que, tras confirmar la publicación, la fila se marca como publicada;</li>
 *   <li>que si la publicación falla, la fila queda <em>sin</em> marcar para reintento (robustez).</li>
 * </ul>
 */
class OutboxRelayTest {

    private static final OutboxRelayProperties PROPS =
            new OutboxRelayProperties("transfer.events", 1000L, 100);

    private OutboxEventEntity pendingEvent(UUID aggregateId, String payload) {
        return new OutboxEventEntity(
                UUID.randomUUID(),
                TransferConfirmedEvent.AGGREGATE_TYPE,
                aggregateId,
                TransferConfirmedEvent.EVENT_TYPE,
                TransferConfirmedEvent.EVENT_VERSION,
                payload);
    }

    @SuppressWarnings("unchecked")
    @Test
    void publicaEventoPendienteConClaveHeadersYLoMarcaPublicado() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

        UUID aggregateId = UUID.randomUUID();
        String payload = "{\"transferId\":\"" + aggregateId + "\"}";
        OutboxEventEntity event = pendingEvent(aggregateId, payload);
        when(repository.findByPublishedFalseOrderByOccurredAtAsc()).thenReturn(List.of(event));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        OutboxRelay relay = new OutboxRelay(repository, kafka, PROPS);
        int published = relay.publishPending();

        assertThat(published).isEqualTo(1);

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("transfer.events");
        // Clave = aggregateId para preservar el orden por partición.
        assertThat(record.key()).isEqualTo(aggregateId.toString());
        // El valor es el JSON ya almacenado en la outbox.
        assertThat(record.value()).isEqualTo(payload);

        // Esquema versionado en headers (criterio 4).
        Header typeHeader = record.headers().lastHeader(OutboxRelay.HEADER_EVENT_TYPE);
        Header versionHeader = record.headers().lastHeader(OutboxRelay.HEADER_EVENT_VERSION);
        assertThat(typeHeader).isNotNull();
        assertThat(versionHeader).isNotNull();
        assertThat(new String(typeHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo(TransferConfirmedEvent.EVENT_TYPE);
        assertThat(new String(versionHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo(Integer.toString(TransferConfirmedEvent.EVENT_VERSION));

        // Tras confirmar la publicación, la fila queda marcada como publicada.
        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void fallaAlPublicarDejaLaFilaSinMarcarParaReintento() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

        OutboxEventEntity event = pendingEvent(UUID.randomUUID(), "{}");
        when(repository.findByPublishedFalseOrderByOccurredAtAsc()).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker caído"));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(failed);

        OutboxRelay relay = new OutboxRelay(repository, kafka, PROPS);
        int published = relay.publishPending();

        // No se publicó nada y la fila sigue pendiente (published = false), lista para reintento.
        assertThat(published).isZero();
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPublishedAt()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void sinPendientesNoPublicaNada() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(repository.findByPublishedFalseOrderByOccurredAtAsc()).thenReturn(List.of());

        OutboxRelay relay = new OutboxRelay(repository, kafka, PROPS);
        int published = relay.publishPending();

        assertThat(published).isZero();
        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void detieneElLoteEnElPrimerFalloYPreservaElOrden() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

        OutboxEventEntity first = pendingEvent(UUID.randomUUID(), "{\"n\":1}");
        OutboxEventEntity second = pendingEvent(UUID.randomUUID(), "{\"n\":2}");
        when(repository.findByPublishedFalseOrderByOccurredAtAsc())
                .thenReturn(List.of(first, second));

        CompletableFuture<SendResult<String, String>> ok =
                CompletableFuture.completedFuture(mock(SendResult.class));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        // El primer envío es exitoso, el segundo falla.
        when(kafka.send(any(ProducerRecord.class))).thenReturn(ok).thenReturn(failed);

        OutboxRelay relay = new OutboxRelay(repository, kafka, PROPS);
        int published = relay.publishPending();

        // Solo el primero se marca; el segundo queda pendiente y no se intentan eventos posteriores.
        assertThat(published).isEqualTo(1);
        assertThat(first.isPublished()).isTrue();
        assertThat(second.isPublished()).isFalse();
    }
}
