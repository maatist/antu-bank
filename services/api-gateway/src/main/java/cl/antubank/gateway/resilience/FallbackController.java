package cl.antubank.gateway.resilience;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Controlador de fallback del circuit breaker del gateway (tarea 9.2, Requisito 9, criterio 4).
 *
 * <p>Cuando un servicio downstream falla o excede su timeout, el filtro {@code CircuitBreaker} de
 * Spring Cloud Gateway (respaldado por Resilience4j reactivo) reenvía la petición a estas rutas
 * ({@code forward:/fallback/...}). La respuesta es <b>elegante y degradada</b>: {@code 503 Service
 * Unavailable} con cuerpo {@link ProblemDetail} (RFC 7807), coherente con el manejo de errores del
 * resto de los servicios.
 *
 * <p>El mensaje es <b>localizable</b> es/en según el header {@code Accept-Language} (es-CL por
 * defecto), en línea con la política i18n del proyecto. El controlador es <b>reactivo</b>: devuelve
 * {@code Mono<ResponseEntity<ProblemDetail>>}.
 *
 * <p>Estas rutas se registran como públicas en {@code SecurityConfig} ({@code /fallback/**}) para
 * que el fallback responda aunque la petición original fuese autenticada.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final URI PROBLEM_TYPE =
            URI.create("https://antubank.cl/problems/service-unavailable");

    @GetMapping("/accounts")
    public Mono<ResponseEntity<ProblemDetail>> accountsFallback(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return buildFallback("account-service", "Cuentas", "Accounts", acceptLanguage);
    }

    @GetMapping("/transactions")
    public Mono<ResponseEntity<ProblemDetail>> transactionsFallback(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return buildFallback("ledger-service", "Movimientos", "Transactions", acceptLanguage);
    }

    // El fallbackUri de Spring Cloud Gateway hace forward preservando el método HTTP original. Como
    // /api/transfers/** admite GET y POST, esta ruta de fallback acepta ambos verbos.
    @RequestMapping(value = "/transfers", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<ProblemDetail>> transfersFallback(
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return buildFallback("transfer-service", "Transferencias", "Transfers", acceptLanguage);
    }

    /**
     * Construye la respuesta {@code 503} con {@link ProblemDetail} localizado. Determina el idioma a
     * partir de {@code Accept-Language}: si empieza por {@code en} responde en inglés; en cualquier
     * otro caso responde en español (es-CL por defecto).
     */
    private Mono<ResponseEntity<ProblemDetail>> buildFallback(String service, String labelEs,
            String labelEn, String acceptLanguage) {
        boolean english = prefersEnglish(acceptLanguage);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(PROBLEM_TYPE);
        if (english) {
            problem.setTitle("Service temporarily unavailable");
            problem.setDetail("The " + labelEn + " service is temporarily unavailable. "
                    + "Please try again in a few moments.");
        } else {
            problem.setTitle("Servicio temporalmente no disponible");
            problem.setDetail("El servicio de " + labelEs + " no está disponible por el momento. "
                    + "Vuelve a intentarlo en unos instantes.");
        }
        problem.setProperty("service", service);

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem));
    }

    /** {@code true} si el cliente prefiere inglés según {@code Accept-Language}. */
    private static boolean prefersEnglish(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return false;
        }
        return acceptLanguage.trim().toLowerCase(java.util.Locale.ROOT).startsWith("en");
    }
}
