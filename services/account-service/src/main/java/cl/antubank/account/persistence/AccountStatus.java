package cl.antubank.account.persistence;

/**
 * Estado de una cuenta bancaria.
 */
public enum AccountStatus {

    /** Cuenta activa y operativa. */
    ACTIVE,

    /** Cuenta bloqueada temporalmente. */
    BLOCKED,

    /** Cuenta cerrada. */
    CLOSED
}
