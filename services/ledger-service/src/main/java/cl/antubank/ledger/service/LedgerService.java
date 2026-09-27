package cl.antubank.ledger.service;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.ledger.api.EntryRequest;
import cl.antubank.ledger.api.RegisterTransactionRequest;
import cl.antubank.ledger.domain.LedgerEntry;
import cl.antubank.ledger.domain.LedgerTransaction;
import cl.antubank.ledger.persistence.LedgerTransactionMapper;
import cl.antubank.ledger.persistence.LedgerTransactionRepository;
import cl.antubank.ledger.persistence.ProcessedTransferEventEntity;
import cl.antubank.ledger.persistence.ProcessedTransferEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de aplicación del ledger: traduce las solicitudes de la API al agregado de dominio
 * {@link LedgerTransaction}, que impone el invariante Σ = 0 al construirse, y persiste el
 * resultado (ver requirements.md, Requisito 3, criterios 1 y 2).
 *
 * <p>La validación contable (balance, moneda única, montos positivos) es responsabilidad del
 * dominio; este servicio se limita a construir los value objects a partir de la moneda de la
 * solicitud y los minor units de cada asiento, y a delegar la persistencia en cascada.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerTransactionRepository transactionRepository;
    private final ProcessedTransferEventRepository processedEventRepository;

    public LedgerService(LedgerTransactionRepository transactionRepository,
                         ProcessedTransferEventRepository processedEventRepository) {
        this.transactionRepository = transactionRepository;
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Registra una transacción contable de doble entrada.
     *
     * <p>Construye el agregado de dominio (que valida Σ = 0 y demás invariantes) y lo persiste.
     * Si la transacción está desbalanceada, el dominio lanza
     * {@link cl.antubank.ledger.domain.UnbalancedTransactionException}, que el manejador global
     * traduce a un {@code ProblemDetail}.
     *
     * @param request datos de la transacción a registrar.
     * @return el agregado de dominio persistido (con su identificador asignado).
     */
    @Transactional
    public LedgerTransaction register(RegisterTransactionRequest request) {
        List<LedgerEntry> entries = request.entries().stream()
                .map(entry -> toDomainEntry(request, entry))
                .toList();

        LedgerTransaction transaction = LedgerTransaction.of(request.reference(), entries);
        transactionRepository.save(LedgerTransactionMapper.toEntity(transaction));
        return transaction;
    }

    private LedgerEntry toDomainEntry(RegisterTransactionRequest request, EntryRequest entry) {
        Money amount = Money.ofMinor(entry.amountMinor(), request.currency());
        return LedgerEntry.of(entry.accountId(), entry.type(), amount);
    }

    /**
     * Asienta una transferencia confirmada de forma <strong>idempotente</strong> generando la
     * transacción de doble entrada correspondiente: débito a la cuenta origen y crédito a la cuenta
     * destino por el mismo monto (Requisito 5, criterios 3 y 5; design.md, sección 5.2).
     *
     * <p><strong>Idempotencia.</strong> Antes de asentar se verifica si el {@code transferId} ya
     * fue procesado (tabla {@code processed_transfer_event}); si lo está, no se hace nada (evita
     * duplicar asientos ante una re-entrega del mismo evento por la garantía "al menos una vez" de
     * Kafka). La marca de procesado y el asiento se escriben en la misma transacción (ambos o
     * ninguno). Ante entregas concurrentes, la clave primaria de {@code transfer_id} garantiza que
     * solo una prospere: la otra recibe una violación de integridad que aquí se interpreta como
     * "ya asentado" y se ignora silenciosamente. El {@code transferId} se conserva además como
     * {@code reference} de la transacción para trazabilidad.
     *
     * @param transferId           id de la transferencia; se usa como referencia idempotente.
     * @param sourceAccountId      cuenta origen (débito).
     * @param destinationAccountId cuenta destino (crédito).
     * @param amount               monto de la transferencia (débito y crédito por igual importe).
     * @return la transacción asentada, o {@link Optional#empty()} si el evento ya se había procesado.
     */
    @Transactional
    public Optional<LedgerTransaction> settleTransfer(
            UUID transferId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            Money amount) {

        // Primera línea de defensa: verificación de existencia por transferId ya procesado.
        if (processedEventRepository.existsById(transferId)) {
            log.debug("Transferencia {} ya asentada; se ignora el evento duplicado", transferId);
            return Optional.empty();
        }

        LedgerTransaction transaction = LedgerTransaction.of(transferId.toString(), List.of(
                LedgerEntry.debit(sourceAccountId, amount),
                LedgerEntry.credit(destinationAccountId, amount)));

        try {
            // Marca de idempotencia y asiento en la MISMA transacción: ambos o ninguno. El flush
            // fuerza la inserción de la marca para detectar de inmediato una colisión de PK por una
            // entrega concurrente del mismo evento.
            processedEventRepository.saveAndFlush(new ProcessedTransferEventEntity(transferId));
            transactionRepository.save(LedgerTransactionMapper.toEntity(transaction));
        } catch (DataIntegrityViolationException e) {
            // Segunda línea de defensa: colisión de clave primaria en processed_transfer_event por
            // una entrega concurrente del mismo evento. Se trata como ya procesado (idempotencia).
            log.debug("Colisión al marcar la transferencia {} como procesada; "
                    + "otra entrega la asentó primero, se ignora", transferId);
            return Optional.empty();
        }
        return Optional.of(transaction);
    }

    /**
     * Variante que recibe monto en minor units y moneda, conveniente para el consumer de eventos.
     *
     * @see #settleTransfer(UUID, UUID, UUID, Money)
     */
    public Optional<LedgerTransaction> settleTransfer(
            UUID transferId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amountMinor,
            Currency currency) {
        return settleTransfer(
                transferId, sourceAccountId, destinationAccountId,
                Money.ofMinor(amountMinor, currency));
    }
}
