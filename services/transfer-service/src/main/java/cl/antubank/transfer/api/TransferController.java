package cl.antubank.transfer.api;

import cl.antubank.transfer.service.TransferResult;
import cl.antubank.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de transferencias idempotentes.
 *
 * <p>Toda transferencia exige el header {@code Idempotency-Key} (ver requirements.md, Requisito 4;
 * design.md, sección 6.1):
 * <ul>
 *   <li>Primera solicitud con una clave nueva: se procesa y persiste el resultado junto a la
 *       clave; responde {@code 201 Created}.</li>
 *   <li>Solicitudes posteriores con la misma clave: se retorna el mismo resultado sin aplicar un
 *       nuevo movimiento; responde {@code 200 OK}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/transfers")
@Validated
@Tag(name = "Transferencias", description = "Transferencias idempotentes en CLP")
public class TransferController {

    /** Nombre del header que porta la clave de idempotencia. */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    /**
     * Inicia una transferencia de forma idempotente respecto al header {@code Idempotency-Key}.
     *
     * @param idempotencyKey clave de idempotencia (obligatoria; su ausencia produce un error
     *                       {@code ProblemDetail}).
     * @param request        datos de la transferencia.
     * @return {@code 201 Created} para una transferencia nueva; {@code 200 OK} al reproducir el
     *         resultado de una clave ya vista.
     */
    @PostMapping
    @Operation(summary = "Iniciar una transferencia idempotente",
            description = "Requiere el header Idempotency-Key. La misma clave retorna el mismo "
                    + "resultado sin duplicar el movimiento.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transferencia creada"),
            @ApiResponse(responseCode = "200",
                    description = "Resultado reproducido (misma Idempotency-Key)"),
            @ApiResponse(responseCode = "400",
                    description = "Solicitud inválida o falta el header Idempotency-Key")
    })
    public ResponseEntity<TransferResponse> create(
            @Parameter(description = "Clave de idempotencia del cliente", required = true)
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {

        TransferResult result = service.transfer(idempotencyKey, request);
        TransferResponse body = TransferResponse.from(result.transfer());

        if (result.replayed()) {
            // Reproducción idempotente: mismo cuerpo, sin duplicar el movimiento.
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/transfers/" + body.id()))
                .header(HttpHeaders.LOCATION, "/transfers/" + body.id())
                .body(body);
    }

    /**
     * Lista el historial de transferencias de una cuenta (movimientos en los que participa como
     * origen o destino), de la más reciente a la más antigua.
     *
     * <p>Es una consulta de solo lectura que sustenta la agregación del BFF GraphQL
     * ({@code me { transfers }}, tarea 9.3; Requisito 9, criterio 5): el gateway consulta este
     * endpoint por cada cuenta del titular y consolida el historial. Exige autenticación (el
     * gateway propaga el JWT del usuario); a diferencia de {@code POST /transfers} —operación de
     * cliente que exige el rol {@code CUSTOMER}— leer el historial solo requiere un token válido.
     *
     * @param accountId cuenta cuyo historial se consulta (origen o destino).
     * @return la lista de transferencias de la cuenta, ordenada por fecha de creación descendente.
     */
    @GetMapping
    @Operation(summary = "Listar el historial de transferencias de una cuenta",
            description = "Retorna las transferencias en las que la cuenta participa como origen o "
                    + "destino, de la más reciente a la más antigua. Alimenta el historial del BFF "
                    + "GraphQL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de la cuenta"),
            @ApiResponse(responseCode = "400", description = "Parámetro accountId inválido o ausente")
    })
    public List<TransferResponse> history(
            @Parameter(description = "Identificador de la cuenta", required = true)
            @RequestParam("accountId") UUID accountId) {
        return service.history(accountId).stream()
                .map(TransferResponse::from)
                .toList();
    }
}
