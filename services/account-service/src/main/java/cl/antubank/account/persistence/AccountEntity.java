package cl.antubank.account.persistence;

import cl.antubank.domain.account.AccountType;
import cl.antubank.domain.account.ChileanBank;
import cl.antubank.domain.identity.Rut;
import cl.antubank.domain.money.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA que representa una cuenta bancaria.
 *
 * <p>El saldo no se almacena aquí: la fuente de verdad del saldo es el ledger-service
 * (contabilidad de doble entrada). Esta entidad describe la cuenta y su titular.
 */
@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Convert(converter = RutConverter.class)
    @Column(name = "holder_rut", nullable = false, length = 20)
    private Rut holderRut;

    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank", nullable = false, length = 40)
    private ChileanBank bank;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountEntity() {
        // Requerido por JPA.
    }

    public AccountEntity(UUID id, Rut holderRut, String holderName, AccountType accountType,
                         ChileanBank bank, Currency currency, AccountStatus status) {
        this.id = id;
        this.holderRut = holderRut;
        this.holderName = holderName;
        this.accountType = accountType;
        this.bank = bank;
        this.currency = currency;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Rut getHolderRut() {
        return holderRut;
    }

    public String getHolderName() {
        return holderName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public ChileanBank getBank() {
        return bank;
    }

    public Currency getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
