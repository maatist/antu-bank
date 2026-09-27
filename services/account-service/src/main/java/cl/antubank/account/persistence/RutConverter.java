package cl.antubank.account.persistence;

import cl.antubank.domain.identity.Rut;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter JPA para el value object {@link Rut}.
 *
 * <p>Persiste el RUT en su forma canónica ({@code cuerpo-dv}, ej. {@code 12345678-5}) y lo
 * reconstruye validándolo al leer.
 */
@Converter(autoApply = true)
public class RutConverter implements AttributeConverter<Rut, String> {

    @Override
    public String convertToDatabaseColumn(Rut attribute) {
        return attribute == null ? null : attribute.toCanonical();
    }

    @Override
    public Rut convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Rut.of(dbData);
    }
}
