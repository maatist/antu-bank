package cl.antubank.transfer.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link IdempotencyKeyEntity}.
 *
 * <p>Permite recuperar la transferencia previamente asociada a una clave de idempotencia para
 * retornar el mismo resultado ante reintentos (usado a partir de la tarea 4.2).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    Optional<IdempotencyKeyEntity> findByIdempotencyKey(String idempotencyKey);
}
