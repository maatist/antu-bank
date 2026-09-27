package cl.antubank.account.service;

import java.util.UUID;

/**
 * Se lanza cuando no existe una cuenta con el identificador solicitado.
 */
public class AccountNotFoundException extends RuntimeException {

    private final UUID accountId;

    public AccountNotFoundException(UUID accountId) {
        super("No existe la cuenta " + accountId);
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
