package cl.antubank.transfer.service;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.transfer.api.CreateTransferRequest;
import cl.antubank.transfer.persistence.IdempotencyKeyRepository;
import cl.antubank.transfer.persistence.TransferEntity;
import cl.antubank.transfer.persistence.TransferRepository;
import cl.antubank.transfer.saga.TransferSagaOrchestrator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de transferencias idempotentes.
 *
 * <p>La idempotencia se implementa así (ver requirements.md, Requisito 4; design.md, sección 6.1):
 * <ol>
 *   <li>Si ya existe la {@code Idempotency-Key}, se recupera la transferencia asociada y se
 *       retorna el mismo resultado sin aplicar un nuevo movimiento (criterio 2).</li>
 *   <li>Si la clave es nueva, se procesa la transferencia y se persisten, en la misma transacción,
 *       tanto la {@link TransferEntity} (resultado) como la {@link IdempotencyKeyEntity} que las
 *       vincula (criterio 1).</li>
 * </ol>
 *
 * <p><strong>Concurrencia (Requisito 4, criterio 3; tarea 4.4).</strong> La restricción única
 * {@code uq_idempotency_key} sobre {@code idem_key} es la barrera que garantiza que dos (o más)
 * solicitudes simultáneas con la misma clave apliquen <em>un solo</em> movimiento: solo una
 * transacción logra insertar la clave; el resto choca con la violación de unicidad. Para que ese
 * choque no degenere en un error, el procesamiento de una clave nueva ocurre en una transacción
 * <em>propia</em> ({@link Propagation#REQUIRES_NEW}) que hace {@code flush} explícito: así la
 * violación se detecta y se materializa como {@link DataIntegrityViolationException} <em>dentro</em>
 * del bloque {@code try}, revirtiendo únicamente esa transacción interna. La transacción externa
 * (la de este método) permanece válida y puede releer la clave ya confirmada por la solicitud
 * ganadora, reproduciendo su resultado en lugar de duplicar el movimiento.
 *
 * <p>Sin la transacción interna con {@code flush}, la violación afloraría recién al confirmar la
 * transacción única del método —fuera del {@code try}— y con el contexto de persistencia ya
 * abortado, imposibilitando la relectura; de ahí la separación en dos límites transaccionales.
 *
 * <p>Alcance de la tarea 4.3 (flujo síncrono, aún sin Kafka): antes de confirmar una transferencia
 * nueva se valida que la cuenta origen tenga fondos suficientes consultando el saldo al
 * ledger-service (criterio 5); si no los tiene, se rechaza y no se registra ningún asiento. Al
 * confirmar, se invoca directamente al ledger para registrar la doble entrada (débito origen,
 * crédito destino), logrando un flujo end-to-end temprano transferencia → asiento → saldo. El
 * monto se maneja en CLP como moneda principal (criterio 4).
 *
 * <p>La validación de fondos y la invocación al ledger ocurren <strong>solo</strong> en el camino
 * de clave nueva. Un reintento con la misma {@code Idempotency-Key} reproduce el resultado
 * almacenado sin volver a invocar al ledger (criterio 2), evitando asientos duplicados.
 *
 * <p>Alcance de la tarea 5.1 (Outbox pattern, lado de persistencia): al confirmar una transferencia
 * nueva se escribe además un evento {@link TransferConfirmedEvent} en la tabla {@code outbox}
 * <strong>dentro de la misma transacción</strong> que la {@link TransferEntity} y su
 * {@link IdempotencyKeyEntity} (ver requirements.md, Requisito 5, criterio 1; design.md sección 6.2
 * y ADR-005). Esto resuelve el problema de dual-write DB↔Kafka: el evento se persiste atómicamente
 * con el resultado de negocio. Si la transacción se revierte (fondos insuficientes, colisión de
 * idempotencia, etc.), tampoco queda escrita la fila de outbox.
 *
 * <p>Nota: la invocación síncrona al ledger se conserva como paso incremental. El relay (tarea 5.2)
 * publicará los eventos pendientes de la outbox a Kafka y ledger-service (tarea 5.3) los consumirá
 * para asentar, reemplazando entonces esta invocación directa.
 *
 * <p>Alcance de la tarea 6.1 (saga orquestada con estado persistido): el procesamiento de una clave
 * nueva ya no ejecuta los pasos inline, sino que delega en {@link TransferSagaOrchestrator}, que
 * coordina de forma explícita <em>reservar fondos → asentar → confirmar</em> y persiste el estado de
 * la saga tras cada transición (ver requirements.md, Requisito 6, criterios 1 y 4; design.md sección
 * 6.3; ADR-006). {@code TransferService} mantiene la responsabilidad de idempotencia/concurrencia
 * (la barrera de los dos límites transaccionales descrita arriba) y delega la mecánica de negocio en
 * la saga. La orquestación ocurre dentro de la misma transacción {@link Propagation#REQUIRES_NEW},
 * conservando la atomicidad transferencia + clave + outbox + estado de saga y, por tanto, todo el
 * comportamiento observable de las tareas 4 y 5.
 */
@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransferSagaOrchestrator sagaOrchestrator;
    // Auto-referencia (lazy para evitar el ciclo en la construcción del bean): necesaria para que
    // la invocación a processNewTransfer atraviese el proxy de Spring y aplique @Transactional
    // (REQUIRES_NEW). Una llamada directa this.processNewTransfer(...) omitiría el proxy.
    private final TransferService self;

    public TransferService(TransferRepository transferRepository,
                           IdempotencyKeyRepository idempotencyKeyRepository,
                           TransferSagaOrchestrator sagaOrchestrator,
                           @Lazy TransferService self) {
        this.transferRepository = transferRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.sagaOrchestrator = sagaOrchestrator;
        this.self = self;
    }

    /**
     * Procesa una transferencia de forma idempotente respecto a la {@code idempotencyKey}.
     *
     * @param idempotencyKey valor del header {@code Idempotency-Key} (obligatorio, no en blanco).
     * @param request        datos de la transferencia.
     * @return el resultado, indicando si se creó ahora o si se reprodujo uno previo.
     * @throws MissingIdempotencyKeyException si la clave es nula o está en blanco.
     */
    @Transactional
    public TransferResult transfer(String idempotencyKey, CreateTransferRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }

        // (1) Clave ya vista: reproducir el mismo resultado sin aplicar un nuevo movimiento.
        var existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return TransferResult.replayed(loadTransfer(existing.get().getTransferId()));
        }

        // (2) Clave nueva: procesar en una transacción propia que hace flush, de modo que una
        // colisión concurrente por la misma clave se detecte AQUÍ como DataIntegrityViolationException
        // y revierta solo esa transacción interna (sin abortar la de este método).
        try {
            TransferEntity transfer = self.processNewTransfer(idempotencyKey, request);
            return TransferResult.created(transfer);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // (3) Otra solicitud concurrente ganó la carrera por la misma clave: en vez de
            // duplicar el movimiento, se reproduce el resultado que esa solicitud confirmó. La
            // relectura ocurre en la transacción (aún válida) de este método.
            return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                    .map(key -> TransferResult.replayed(loadTransfer(key.getTransferId())))
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    /**
     * Procesa y persiste una transferencia para una clave nueva en una transacción independiente,
     * delegando la orquestación de los pasos en la saga.
     *
     * <p>Se ejecuta con {@link Propagation#REQUIRES_NEW}: el {@link TransferSagaOrchestrator} corre
     * dentro de esta misma transacción y fuerza el {@code flush} de la clave de idempotencia
     * (vía {@code saveAndFlush}) para que, ante una colisión concurrente por la misma clave, la
     * violación de la restricción única {@code uq_idempotency_key} se manifieste dentro de esta
     * transacción y provoque el rollback solo de ella (incluida la fila de estado de la saga),
     * dejando intacta la transacción llamante que reproducirá el resultado ganador (Requisito 4,
     * criterio 3).
     *
     * <p>La orquestación ejecuta los pasos <em>reservar fondos → asentar → confirmar</em> y persiste
     * el estado de la saga tras cada transición (Requisito 6, criterios 1 y 4). Como todo ocurre en
     * esta única transacción, la atomicidad transferencia + clave + outbox + estado de saga se
     * mantiene: si cualquier paso falla, no queda nada persistido.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferEntity processNewTransfer(String idempotencyKey, CreateTransferRequest request) {
        Money amount = Money.ofMinor(request.amountMinor(), Currency.CLP);
        return sagaOrchestrator.ejecutar(
                idempotencyKey,
                request.sourceAccountId(),
                request.destinationAccountId(),
                amount);
    }

    /**
     * Historial de transferencias de una cuenta (como origen o destino), de la más reciente a la
     * más antigua. Es una consulta de solo lectura que sustenta el endpoint {@code
     * GET /transfers?accountId=} (tarea 9.4), consumido por el BFF GraphQL para resolver
     * {@code me { transfers }} (Requisito 9, criterio 5).
     *
     * @param accountId cuenta cuyo historial se solicita.
     * @return las transferencias de la cuenta, ordenadas por fecha de creación descendente.
     */
    @Transactional(readOnly = true)
    public List<TransferEntity> history(UUID accountId) {
        return transferRepository.findHistoryByAccountId(accountId);
    }

    private TransferEntity loadTransfer(UUID transferId) {
        // Invariante de integridad referencial: la clave siempre apunta a una transferencia
        // existente (FK en la migración V2). Si faltara, es una inconsistencia de datos.
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency-Key sin transferencia asociada: " + transferId));
    }
}
