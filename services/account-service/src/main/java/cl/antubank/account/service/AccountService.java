package cl.antubank.account.service;

import cl.antubank.account.api.AccountResponse;
import cl.antubank.account.api.CreateAccountRequest;
import cl.antubank.account.persistence.AccountEntity;
import cl.antubank.account.persistence.AccountRepository;
import cl.antubank.account.persistence.AccountStatus;
import cl.antubank.domain.identity.Rut;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de gestión de cuentas.
 */
@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        // El RUT ya fue validado por Bean Validation; aquí se materializa el value object.
        Rut holderRut = Rut.of(request.holderRut());
        AccountEntity entity = new AccountEntity(
                UUID.randomUUID(),
                holderRut,
                request.holderName(),
                request.accountType(),
                request.bank(),
                request.currency(),
                AccountStatus.ACTIVE
        );
        AccountEntity saved = repository.save(entity);
        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(UUID id) {
        return repository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findByHolderRut(String rut) {
        Rut holderRut = Rut.of(rut);
        return repository.findByHolderRut(holderRut).stream()
                .map(AccountResponse::from)
                .toList();
    }
}
