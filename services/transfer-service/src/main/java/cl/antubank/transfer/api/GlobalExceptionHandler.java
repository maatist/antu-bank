package cl.antubank.transfer.api;

import cl.antubank.transfer.service.InsufficientFundsException;
import cl.antubank.transfer.service.MissingIdempotencyKeyException;
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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejo centralizado de errores en formato RFC 7807 (ProblemDetail) para el transfer-service.
 *
 * <p>Los títulos/detalles se resuelven vía {@link MessageSource} usando el locale del request
 * (cabecera {@code Accept-Language}), con español como idioma por defecto. Se replica el patrón
 * establecido en account-service y ledger-service.
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
     * Parámetro con tipo inválido (ej. un UUID mal formado en el cuerpo/params).
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
     * Falta el header {@code Idempotency-Key} (Spring lo detecta como header requerido ausente).
     * Se trata igual que una clave en blanco: la idempotencia exige la clave (Requisito 4).
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return idempotencyKeyMissingProblem();
    }

    /**
     * Header {@code Idempotency-Key} presente pero en blanco (validado en el servicio).
     */
    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ProblemDetail handleMissingIdempotencyKey(MissingIdempotencyKeyException ex) {
        return idempotencyKeyMissingProblem();
    }

    /**
     * Fondos insuficientes en la cuenta origen (Requisito 4, criterio 5). Se responde
     * {@code 422 Unprocessable Entity}: la solicitud es sintácticamente válida pero la regla de
     * negocio impide aplicarla. No se registra ningún asiento en el ledger.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle(msg("error.transfer.insufficientFunds.title", locale));
        problem.setDetail(msg("error.transfer.insufficientFunds.detail", locale));
        problem.setProperty("sourceAccountId", ex.sourceAccountId());
        return problem;
    }

    private ProblemDetail idempotencyKeyMissingProblem() {
        Locale locale = LocaleContextHolder.getLocale();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(msg("error.transfer.idempotencyKey.missing.title", locale));
        problem.setDetail(msg("error.transfer.idempotencyKey.missing.detail", locale));
        return problem;
    }

    private String msg(String code, Locale locale) {
        return messages.getMessage(code, null, code, locale);
    }
}
