package cl.antubank.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value object inmutable que representa una cantidad de dinero en una {@link Currency}.
 *
 * <p>Reglas de dominio (ver requirements.md, Requisito 1):
 * <ul>
 *   <li>Nunca usa tipos de punto flotante; internamente usa {@link BigDecimal}.</li>
 *   <li>El monto se normaliza al {@code scale} de la moneda usando redondeo bancario
 *       ({@link RoundingMode#HALF_EVEN}). Para CLP la escala es 0 (sin decimales).</li>
 *   <li>Las operaciones {@link #plus(Money)} y {@link #minus(Money)} exigen misma moneda.</li>
 *   <li>Igualdad por valor (monto normalizado + moneda).</li>
 * </ul>
 *
 * <p>Este tipo no prohíbe montos negativos por sí mismo (un asiento de débito los necesita).
 * La restricción de "no negativo" se aplica en el borde de negocio que corresponda.
 */
public final class Money {

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.currency = Objects.requireNonNull(currency, "currency no puede ser null");
        this.amount = Objects.requireNonNull(amount, "amount no puede ser null")
                .setScale(currency.scale(), RoundingMode.HALF_EVEN);
    }

    /**
     * Crea un {@code Money} a partir de unidades mayores de la moneda.
     * Ej: {@code ofMajor(new BigDecimal("1000"), CLP)} = $1.000; {@code ofMajor("10.50", USD)} = US$10,50.
     */
    public static Money ofMajor(BigDecimal major, Currency currency) {
        return new Money(major, currency);
    }

    /**
     * Variante conveniente que recibe el monto mayor como {@code String}
     * (evita literales {@code double} accidentales).
     */
    public static Money ofMajor(String major, Currency currency) {
        return new Money(new BigDecimal(major), currency);
    }

    /**
     * Variante conveniente que recibe el monto mayor como {@code long}.
     */
    public static Money ofMajor(long major, Currency currency) {
        return new Money(BigDecimal.valueOf(major), currency);
    }

    /**
     * Crea un {@code Money} a partir de minor units (la menor unidad de la moneda).
     * Ej: para USD, {@code ofMinor(1050, USD)} = US$10,50; para CLP, {@code ofMinor(1000, CLP)} = $1.000.
     */
    public static Money ofMinor(long minorUnits, Currency currency) {
        BigDecimal factor = BigDecimal.TEN.pow(currency.scale());
        BigDecimal major = BigDecimal.valueOf(minorUnits).divide(factor);
        return new Money(major, currency);
    }

    /**
     * @return cero en la moneda indicada.
     */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), currency);
    }

    /**
     * @return el monto expresado en minor units (entero). Ej: US$10,50 -> 1050; $1.000 CLP -> 1000.
     */
    public long toMinorUnits() {
        return amount.movePointRight(currency.scale()).longValueExact();
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Currency currency() {
        return currency;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other no puede ser null");
        if (this.currency != other.currency) {
            throw new CurrencyMismatchException(this.currency, other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        // amount ya está normalizado al scale de la moneda, por lo que compareTo == 0
        // implica igualdad de valor sin ambigüedad de escala.
        return currency == other.currency && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
