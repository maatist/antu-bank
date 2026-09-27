package cl.antubank.transfer.saga;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link TransferSagaEntity}.
 *
 * <p>Permite recuperar el estado persistido de la saga por su transferencia asociada, base para la
 * auditoría (Requisito 6, criterio 4) y, más adelante, para la reanudación/compensación tras un
 * reinicio (tarea 6.2).
 */
public interface TransferSagaRepository extends JpaRepository<TransferSagaEntity, UUID> {

    Optional<TransferSagaEntity> findByTransferId(UUID transferId);
}
