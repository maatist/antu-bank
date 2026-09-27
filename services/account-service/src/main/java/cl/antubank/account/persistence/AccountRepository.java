package cl.antubank.account.persistence;

import cl.antubank.domain.identity.Rut;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA de cuentas.
 */
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    /**
     * Busca cuentas por el RUT del titular. El {@link cl.antubank.account.persistence.RutConverter}
     * traduce el value object a su forma canónica para la consulta.
     */
    List<AccountEntity> findByHolderRut(Rut holderRut);
}
