package cl.antubank.account.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

/**
 * Valida que un texto corresponda a un RUT chileno con dígito verificador correcto (módulo 11).
 */
@Documented
@Constraint(validatedBy = RutValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRut {

    String message() default "{error.rut.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
