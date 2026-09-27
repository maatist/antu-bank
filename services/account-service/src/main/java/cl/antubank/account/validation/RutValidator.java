package cl.antubank.account.validation;

import cl.antubank.domain.identity.Rut;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementación de {@link ValidRut}: delega en {@link Rut#isValid(String)}.
 * Un valor nulo se considera válido (usar {@code @NotBlank} para exigir presencia).
 */
public class RutValidator implements ConstraintValidator<ValidRut, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return Rut.isValid(value);
    }
}
