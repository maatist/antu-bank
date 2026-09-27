package cl.antubank.ledger.api;

import cl.antubank.ledger.domain.UnbalancedTransactionException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejo centralizado de errores en formato RFC 7807 (ProblemDetail) para el ledger-service.
 *
 * <p>Los títulos/detalles se resuelven vía {@link MessageSource} usando el locale del request
 * (cabecera {@code Accept-Language}), con español como idioma por defecto. Se replica el patrón
 * establecido en account-service.
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
     * Errores de validación de parámetros (@Validated en el controller).
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
     * Parámetro con tipo inválido (ej. una moneda desconocida en {@code ?currency=}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(msg("error.validation.title", locale));
        problem.setDetail(msg("error.validation.detail", locale));
        problem.setProperty("parameter", ex.getName());
        return problem;
    }

    /**
     * Transacción desbalanceada (Σ ≠ 0): rechazo del invariante contable
     * (Requisito 3, criterio 2).
     */
    @ExceptionHandler(UnbalancedTransactionException.class)
    public ProblemDetail handleUnbalanced(UnbalancedTransactionException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle(msg("error.unbalanced.title", locale));
        problem.setDetail(msg("error.unbalanced.detail", locale));
        return problem;
    }

    /**
     * Violaciones de reglas de dominio al construir la transacción o sus asientos
     * (ej. menos de dos asientos, monedas mezcladas, monto no positivo).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(msg("error.validation.title", locale));
        problem.setDetail(msg("error.transaction.invalid", locale));
        return problem;
    }

    private String msg(String code, Locale locale) {
        return messages.getMessage(code, null, code, locale);
    }
}
