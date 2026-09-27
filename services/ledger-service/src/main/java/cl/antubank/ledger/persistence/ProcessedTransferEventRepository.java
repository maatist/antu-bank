package cl.antubank.ledger.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de marcas de eventos de transferencia procesados
 * ({@link ProcessedTransferEventEntity}).
 *
 * <p>Sustenta el consumo idempotente de eventos (tarea 5.3, Requisito 5): el consumer verifica con
 * {@link #existsById(Object)} si el {@code transferId} ya fue procesado y, de no estarlo, persiste
 * la marca junto al asiento. La clave primaria da la garantía dura ante entregas concurrentes.
 */
public interface ProcessedTransferEventRepository
        extends JpaRepository<ProcessedTransferEventEntity, UUID> {
}
