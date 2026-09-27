package cl.antubank.transfer.saga;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.transfer.ledger.LedgerClient;
import cl.antubank.transfer.outbox.OutboxWriter;
import cl.antubank.transfer.outbox.TransferConfirmedEvent;
import cl.antubank.transfer.persistence.IdempotencyKeyEntity;
import cl.antubank.transfer.persistence.IdempotencyKeyRepository;
import cl.antubank.transfer.persistence.TransferEntity;
import cl.antubank.transfer.persistence.TransferRepository;
import cl.antubank.transfer.persistence.TransferStatus;
import cl.antubank.transfer.service.InsufficientFundsException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Orquestador de la <strong>saga de transferencia</strong> (Requisito 6; design.md, sección 6.3;
 * ADR-006: saga orquestada).
 *
 * <p>Coordina de forma explícita los pasos <em>reservar fondos → asentar → confirmar</em>
 * (Requisito 6, criterio 1) y persiste el estado de la saga tras cada transición (criterio 4),
 * de modo que el progreso sobreviva reinicios y sea auditable. El estado se materializa en
 * {@link TransferSagaEntity}.
 *
 * <p><strong>Transaccionalidad.</strong> Este orquestador NO abre una transacción propia: se ejecuta
 * <em>dentro</em> de la transacción de negocio del llamador ({@code TransferService.processNewTransfer},
 * con {@code REQUIRES_NEW}). Así, la transferencia, su clave de idempotencia, el evento de outbox y
 * la fila de estado de la saga se confirman o revierten <strong>atómicamente</strong>. Esto preserva
 * las garantías establecidas en tareas anteriores:
 * <ul>
 *   <li>idempotencia y concurrencia: la clave se reclama con {@code saveAndFlush} <em>antes</em> de
 *       tocar el ledger, para que un perdedor de la carrera no registre un segundo asiento
 *       (Requisito 4, criterio 3);</li>
 *   <li>outbox: el evento se escribe en la misma transacción de negocio (Requisito 5, criterio 1);</li>
 *   <li>rechazo por fondos insuficientes antes de persistir nada (Requisito 4, criterio 5).</li>
 * </ul>
 *
 * <p><strong>Compensación por paso ante fallo (tarea 6.2; Requisito 6, criterios 2 y 3).</strong>
 * Si un paso falla, el orquestador ejecuta las compensaciones de los pasos ya completados en orden
 * inverso (design.md 6.3). Hay tres escenarios según <em>dónde</em> falle, distinguidos por si ya
 * existe un efecto externo (el asiento en el ledger):
 * <ul>
 *   <li><strong>Fallo al reservar</strong> (antes de tocar el ledger): no hay nada que compensar; se
 *       propaga el fallo y la reversión de la transacción de negocio deja el estado local limpio. La
 *       saga se marca {@link SagaState#FALLIDA}.</li>
 *   <li><strong>Fallo al asentar</strong> (la llamada al ledger falla, sin registrar el asiento): no
 *       hay efecto externo; se compensa lógicamente la reserva y se propaga el fallo, revirtiendo la
 *       transacción local por completo. Saldos íntegros sin asiento inverso.</li>
 *   <li><strong>Fallo al confirmar</strong> (el asiento YA se registró en el ledger): revertir la
 *       transacción local NO desharía ese asiento externo. Por eso la compensación se ejecuta
 *       explícitamente —se registra un <em>asiento inverso</em> (reversa) en el ledger, que es
 *       inmutable— y se confirma de forma <strong>durable</strong>: la saga queda
 *       {@link SagaState#COMPENSADA}, la transferencia {@link TransferStatus#REJECTED} y el método
 *       retorna sin relanzar, de modo que esta transacción COMMITEE el estado compensado. El efecto
 *       neto sobre los saldos es cero (criterio 3).</li>
 * </ul>
 * En todos los casos el estado de la saga (global y por paso, con {@code failure_reason}) queda
 * registrado para auditoría (criterio 4).
 */
@Component
public class TransferSagaOrchestrator {

    private final TransferRepository transferRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransferSagaRepository sagaRepository;
    private final LedgerClient ledgerClient;
    private final OutboxWriter outboxWriter;
    private final SagaConfirmationStep confirmationStep;

    public TransferSagaOrchestrator(TransferRepository transferRepository,
                                    IdempotencyKeyRepository idempotencyKeyRepository,
                                    TransferSagaRepository sagaRepository,
                                    LedgerClient ledgerClient,
                                    OutboxWriter outboxWriter,
                                    SagaConfirmationStep confirmationStep) {
        this.transferRepository = transferRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.sagaRepository = sagaRepository;
        this.ledgerClient = ledgerClient;
        this.outboxWriter = outboxWriter;
        this.confirmationStep = confirmationStep;
    }

    /**
     * Ejecuta la saga de una transferencia nueva de principio a fin en el camino feliz, dejando
     * persistido el estado de cada transición.
     *
     * <p>Debe invocarse dentro de la transacción de negocio del llamador (ver nota de
     * transaccionalidad de la clase).
     *
     * @param idempotencyKey clave de idempotencia del cliente (ya validada como no vacía).
     * @param sourceAccountId cuenta origen.
     * @param destinationAccountId cuenta destino.
     * @param amount monto a transferir (CLP como moneda principal).
     * @return la transferencia confirmada resultante.
     * @throws InsufficientFundsException si la cuenta origen no tiene fondos suficientes.
     */
    public TransferEntity ejecutar(String idempotencyKey, UUID sourceAccountId,
                                   UUID destinationAccountId, Money amount) {
        // La transferencia nace PENDING; la saga la lleva hasta CONFIRMED al confirmar.
        TransferEntity transfer = transferRepository.save(new TransferEntity(
                UUID.randomUUID(),
                sourceAccountId,
                destinationAccountId,
                amount,
                TransferStatus.PENDING));

        // Estado inicial de la saga: INICIADA, con todos los pasos PENDIENTE (Requisito 6, criterio 4).
        TransferSagaEntity saga = sagaRepository.save(TransferSagaEntity.iniciar(transfer.getId()));

        // --- Paso 1: reservar fondos --------------------------------------------------------
        // Efecto: validar/reservar contra el saldo del ledger (Requisito 4, criterio 5). En el
        // modelo actual el ledger no expone una "reserva" explícita, por lo que reservar equivale a
        // comprobar que hay saldo suficiente antes de asentar. Si no lo hay, se aborta: no hay nada
        // que compensar (design.md 6.3: ReservarFondos --> [*] en fallo). El fallo (típicamente
        // InsufficientFundsException) se propaga: al ocurrir ANTES de cualquier efecto externo, la
        // reversión de esta transacción de negocio deja el estado local limpio (no queda transfer,
        // clave, outbox ni saga). Se marca FALLIDA por claridad de la máquina de estados aunque la
        // fila se revierta junto con el resto.
        try {
            reservarFondos(sourceAccountId, amount);
        } catch (RuntimeException fallo) {
            saga.fallarReserva(fallo.getMessage());
            sagaRepository.save(saga);
            throw fallo;
        }
        saga.reservarFondos();
        sagaRepository.save(saga);

        // --- Paso 2: asentar -----------------------------------------------------------------
        // Reclamar la clave de idempotencia ANTES de asentar y con flush inmediato: si otra
        // solicitud concurrente ya la reclamó, la violación de uq_idempotency_key se lanza aquí y
        // toda la transacción (incluida esta saga) se revierte SIN asentar. Así se mantiene "doble
        // envío = un solo movimiento" (Requisito 4, criterio 3) también con la saga de por medio.
        idempotencyKeyRepository.saveAndFlush(
                new IdempotencyKeyEntity(UUID.randomUUID(), idempotencyKey, transfer.getId()));

        // Escribir el evento TransferConfirmed en la outbox dentro de esta misma transacción de
        // negocio (Outbox pattern, Requisito 5, criterio 1). Se registra al asentar para conservar
        // la atomicidad ya probada por OutboxIntegrationTest.
        outboxWriter.appendTransferConfirmed(new TransferConfirmedEvent(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(),
                transfer.getAmountMinor(),
                transfer.getCurrency()));

        // Registrar la doble entrada en el ledger (invocación directa temporal, tarea 4.3; la tarea
        // 5.3 la reemplaza por el consumo del evento de outbox). El evento ya quedó registrado arriba.
        try {
            asentar(transfer, amount);
        } catch (RuntimeException fallo) {
            // El asiento NO llegó a registrarse en el ledger (la llamada falló), así que no hay
            // efecto externo que revertir: solo resta compensar la reserva (design.md 6.3:
            // Asentar --> CompensarReserva). Como aún no hay side effect externo, se propaga el
            // fallo y la transacción local se revierte por completo (transfer + clave + outbox +
            // saga), dejando los saldos íntegros (criterios 2 y 3) sin necesidad de un asiento
            // inverso. Se registra la intención de compensación por trazabilidad antes de revertir.
            saga.fallarAsentamiento(fallo.getMessage());
            saga.compensarReserva();
            sagaRepository.save(saga);
            throw fallo;
        }
        saga.asentar();
        sagaRepository.save(saga);

        // --- Paso 3: confirmar ---------------------------------------------------------------
        // A diferencia de los pasos anteriores, aquí el asiento YA se registró en el ledger (efecto
        // externo). Si la confirmación falla, revertir la transacción de negocio NO desharía ese
        // asiento (el ledger es inmutable y vive en otro servicio): quedaría un movimiento huérfano
        // y los saldos NO cuadrarían. Por eso la compensación de este caso NO se hace revirtiendo la
        // transacción, sino ejecutándola explícitamente y confirmándola de forma DURABLE:
        //   1) se registra un asiento inverso en el ledger (reverseTransfer, signos intercambiados),
        //      de modo que el efecto neto sobre los saldos sea cero (criterio 3);
        //   2) se marca la saga COMPENSADA con sus pasos COMPENSADO en orden inverso (asiento →
        //      reserva) y la transferencia como REJECTED;
        //   3) se retorna normalmente (sin relanzar), para que esta transacción COMMITEE el estado
        //      compensado. Así la compensación es durable y auditable (Requisito 6, criterios 2 y 4).
        // Nota de idempotencia: la clave ya reclamada queda apuntando a una transferencia REJECTED;
        // un reintento con la misma Idempotency-Key reproducirá ese resultado sin aplicar un nuevo
        // movimiento (Requisito 4, criterio 2), que es el comportamiento correcto para una saga
        // fallida ya compensada.
        try {
            confirmar(transfer);
        } catch (RuntimeException fallo) {
            return compensarTrasAsentar(transfer, saga, amount, fallo.getMessage());
        }
        saga.confirmar();
        sagaRepository.save(saga);

        return transfer;
    }

    /**
     * Compensación de una saga que ya asentó la doble entrada pero falló al confirmar: registra el
     * asiento inverso en el ledger y deja el estado (saga + transferencia) compensado de forma
     * durable, con los saldos íntegros (Requisito 6, criterios 2 y 3; design.md 6.3:
     * {@code CompensarAsiento --> CompensarReserva --> [*] saldos íntegros}).
     */
    private TransferEntity compensarTrasAsentar(TransferEntity transfer, TransferSagaEntity saga,
                                                Money amount, String motivo) {
        saga.fallarConfirmacion(motivo);
        sagaRepository.save(saga);

        // CompensarAsiento: asiento inverso en el ledger (reversa), pues el ledger es inmutable y no
        // permite borrar/actualizar el asiento original.
        ledgerClient.reverseTransfer(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(),
                amount);
        saga.compensarAsiento();

        // CompensarReserva (liberación lógica): cierra la saga como COMPENSADA.
        saga.compensarReserva();
        sagaRepository.save(saga);

        // La transferencia queda RECHAZADA: la operación no se aplicó (fue compensada).
        transfer.setStatus(TransferStatus.REJECTED);
        transferRepository.save(transfer);

        return transfer;
    }

    /**
     * Paso 1 — reservar fondos: valida que la cuenta origen tenga saldo suficiente consultándolo al
     * ledger-service (fuente de verdad). Si {@code disponible < solicitado}, lanza
     * {@link InsufficientFundsException} y la saga aborta sin haber asentado nada.
     */
    private void reservarFondos(UUID sourceAccountId, Money amount) {
        Money available = ledgerClient.balance(sourceAccountId, Currency.CLP);
        if (available.minus(amount).isNegative()) {
            throw new InsufficientFundsException(sourceAccountId, available, amount);
        }
    }

    /**
     * Paso 2 — asentar: registra en el ledger la doble entrada (débito origen, crédito destino).
     */
    private void asentar(TransferEntity transfer, Money amount) {
        ledgerClient.registerTransfer(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(),
                amount);
    }

    /**
     * Paso 3 — confirmar: marca la transferencia como {@link TransferStatus#CONFIRMED}.
     *
     * <p>Se aísla en su propio método —además de por simetría con los otros pasos— para que la
     * confirmación pueda fallar de forma controlada (p. ej. una comprobación post-asentamiento) y el
     * orquestador dispare la compensación con asiento inverso. Un fallo aquí ocurre <em>después</em>
     * de un efecto externo (el asiento en el ledger), por lo que exige compensación explícita.
     */
    private void confirmar(TransferEntity transfer) {
        // Efecto de confirmación (por defecto no-op; punto de extensión). Puede fallar y disparar la
        // compensación con asiento inverso, ya que en este punto el asiento externo ya se registró.
        confirmationStep.confirmar(transfer);
        transfer.setStatus(TransferStatus.CONFIRMED);
        transferRepository.save(transfer);
    }
}
