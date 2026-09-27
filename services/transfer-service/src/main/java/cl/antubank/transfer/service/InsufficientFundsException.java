package cl.antubank.transfer.service;

import cl.antubank.domain.money.Money;
import java.util.UUID;

/**
 * Se lanza cuando la cuenta origen no tiene fondos suficientes para aplicar la transferencia
 * (ver requirements.md, Requisito 4, criterio 5).
 *
 * <p>El saldo se consulta al ledger-service (fuente de verdad) antes de confirmar la transferencia;
 * si {@code disponible < solicitado}, la transferencia se rechaza y no se registra ningún asiento.
 */
public class InsufficientFundsException extends RuntimeException {

    private final UUID sourceAccountId;
    private final transient Money available;
    private final transient Money requested;

    public InsufficientFundsException(UUID sourceAccountId, Money available, Money requested) {
        super("Fondos insuficientes en la cuenta " + sourceAccountId
                + ": disponible=" + available + ", solicitado=" + requested);
        this.sourceAccountId = sourceAccountId;
        this.available = available;
        this.requested = requested;
    }

    public UUID sourceAccountId() {
        return sourceAccountId;
    }

    public Money available() {
        return available;
    }

    public Money requested() {
        return requested;
    }
}
