package cl.antubank.transfer.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de {@link TransferEntity}.
 */
public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {

    /**
     * Historial de transferencias en las que la cuenta participa como origen <em>o</em> destino,
     * ordenado por fecha de creación descendente (más recientes primero).
     *
     * <p>Sustenta el endpoint de historial {@code GET /transfers?accountId=} (tarea 9.4) que el BFF
     * GraphQL consume para resolver {@code me { transfers }} (Requisito 9, criterio 5). Se incluyen
     * ambos sentidos (débito y crédito) porque, desde la perspectiva del titular, el historial de
     * una cuenta comprende tanto el dinero que envió como el que recibió.
     *
     * @param accountId cuenta cuyo historial se consulta (origen o destino).
     * @return transferencias de la cuenta, de la más reciente a la más antigua.
     */
    @Query("""
            SELECT t FROM TransferEntity t
            WHERE t.sourceAccountId = :accountId OR t.destinationAccountId = :accountId
            ORDER BY t.createdAt DESC
            """)
    List<TransferEntity> findHistoryByAccountId(@Param("accountId") UUID accountId);
}
