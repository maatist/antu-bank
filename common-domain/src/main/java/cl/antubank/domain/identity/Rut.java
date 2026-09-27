package cl.antubank.domain.identity;

import java.util.Objects;

/**
 * Value object inmutable que representa un RUT (Rol Único Tributario) chileno.
 *
 * <p>Un RUT se compone de un cuerpo numérico y un dígito verificador (DV) calculado
 * con el algoritmo de módulo 11. El DV puede ser un dígito {@code 0-9} o la letra {@code K}
 * (que representa el valor 10).
 *
 * <p>Reglas de dominio (ver requirements.md, Requisito 1):
 * <ul>
 *   <li>La construcción valida el DV mediante módulo 11; un RUT inválido lanza {@link InvalidRutException}.</li>
 *   <li>{@link #format()} entrega la representación chilena, ej. {@code 12.345.678-5}.</li>
 *   <li>Igualdad por valor (cuerpo + DV normalizado).</li>
 * </ul>
 */
public final class Rut {

    private final int body;   // parte numérica, ej. 12345678
    private final char dv;    // dígito verificador normalizado en mayúscula ('0'..'9' o 'K')

    private Rut(int body, char dv) {
        this.body = body;
        this.dv = dv;
    }

    /**
     * Construye y valida un RUT a partir de un texto que puede venir con o sin puntos y guion.
     * Ejemplos aceptados: {@code "12.345.678-5"}, {@code "12345678-5"}, {@code "123456785"}.
     *
     * @throws InvalidRutException si el formato es inválido o el DV no corresponde.
     */
    public static Rut of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRutException("El RUT no puede ser vacío.");
        }
        String cleaned = raw.replace(".", "").replace("-", "").trim().toUpperCase();
        if (cleaned.length() < 2) {
            throw new InvalidRutException("El RUT es demasiado corto: " + raw);
        }

        String bodyPart = cleaned.substring(0, cleaned.length() - 1);
        char dvPart = cleaned.charAt(cleaned.length() - 1);

        if (!bodyPart.chars().allMatch(Character::isDigit)) {
            throw new InvalidRutException("El cuerpo del RUT debe ser numérico: " + raw);
        }

        int bodyValue;
        try {
            bodyValue = Integer.parseInt(bodyPart);
        } catch (NumberFormatException e) {
            throw new InvalidRutException("Cuerpo de RUT fuera de rango: " + raw);
        }
        if (bodyValue <= 0) {
            throw new InvalidRutException("El cuerpo del RUT debe ser positivo: " + raw);
        }

        char expectedDv = computeDv(bodyValue);
        if (dvPart != expectedDv) {
            throw new InvalidRutException(
                    "Dígito verificador inválido para " + raw + " (esperado " + expectedDv + ").");
        }
        return new Rut(bodyValue, expectedDv);
    }

    /**
     * Indica si un texto corresponde a un RUT válido, sin lanzar excepción.
     */
    public static boolean isValid(String raw) {
        try {
            of(raw);
            return true;
        } catch (InvalidRutException e) {
            return false;
        }
    }

    /**
     * Calcula el dígito verificador (módulo 11) para un cuerpo numérico.
     * Retorna '0'..'9' o 'K'.
     */
    static char computeDv(int body) {
        int sum = 0;
        int multiplier = 2;
        int n = body;
        while (n > 0) {
            sum += (n % 10) * multiplier;
            n /= 10;
            multiplier = (multiplier == 7) ? 2 : multiplier + 1;
        }
        int remainder = 11 - (sum % 11);
        return switch (remainder) {
            case 11 -> '0';
            case 10 -> 'K';
            default -> (char) ('0' + remainder);
        };
    }

    /**
     * @return el RUT en formato chileno con separadores de miles y guion, ej. {@code 12.345.678-5}.
     */
    public String format() {
        String bodyStr = String.format("%,d", body).replace(',', '.');
        return bodyStr + "-" + dv;
    }

    /**
     * @return el RUT sin puntos, con guion, ej. {@code 12345678-5}.
     */
    public String toCanonical() {
        return body + "-" + dv;
    }

    public int body() {
        return body;
    }

    public char dv() {
        return dv;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rut other)) {
            return false;
        }
        return body == other.body && dv == other.dv;
    }

    @Override
    public int hashCode() {
        return Objects.hash(body, dv);
    }

    @Override
    public String toString() {
        return format();
    }
}
