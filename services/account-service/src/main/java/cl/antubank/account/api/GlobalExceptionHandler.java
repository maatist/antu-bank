package cl.antubank.account.api;

import cl.antubank.account.service.AccountNotFoundException;
import cl.antubank.domain.identity.InvalidRutException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejo centralizado de errores en formato RFC 7807 (ProblemDetail).
 * Los títulos/detalles se resuelven vía {@link MessageSource} usando el locale del request
 * (cabecera {@code Accept-Language}), con español como idioma por defecto.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    /**
     * Errores de validación del cuerpo (@Valid sobre @RequestBody).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(msg("error.validation.title", locale));
        problem.setDetail(msg("error.validation.detail", locale));

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (var error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    /**
     * Errores de validación de parámetros (@Validated en el controller, ej. query param).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleParamValidation(ConstraintViolationException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(msg("error.validation.title", locale));
        problem.setDetail(msg("error.validation.detail", locale));

        List<String> violations = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        problem.setProperty("errors", violations);
        return problem;
    }

    /**
     * RUT inválido detectado fuera de Bean Validation (ej. al materializar el value object).
     */
    @ExceptionHandler(InvalidRutException.class)
    public ProblemDetail handleInvalidRut(InvalidRutException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(msg("error.validation.title", locale));
        problem.setDetail(msg("error.rut.invalid", locale));
        return problem;
    }

    /**
     * Cuenta inexistente.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleNotFound(AccountNotFoundException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle(msg("error.account.notfound.title", locale));
        problem.setDetail(msg("error.account.notfound.detail", locale));
        problem.setProperty("accountId", ex.accountId());
        return problem;
    }

    private String msg(String code, Locale locale) {
        return messages.getMessage(code, null, code, locale);
    }
}
