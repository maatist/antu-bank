package cl.antubank.ledger.api;

import cl.antubank.domain.money.Currency;
import cl.antubank.ledger.domain.LedgerTransaction;
import cl.antubank.ledger.service.AccountBalance;
import cl.antubank.ledger.service.BalanceService;
import cl.antubank.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del ledger de doble entrada.
 *
 * <p>Expone dos operaciones (ver requirements.md, Requisito 3):
 * <ul>
 *   <li>Registrar una transacción contable balanceada (Σ = 0); las desbalanceadas se rechazan.</li>
 *   <li>Consultar el saldo derivado de una cuenta a partir de sus asientos.</li>
 * </ul>
 */
@RestController
@RequestMapping("/transactions")
@Validated
@Tag(name = "Ledger", description = "Contabilidad de doble entrada: asientos y saldos derivados")
public class LedgerController {

    private final LedgerService ledgerService;
    private final BalanceService balanceService;

    public LedgerController(LedgerService ledgerService, BalanceService balanceService) {
        this.ledgerService = ledgerService;
        this.balanceService = balanceService;
    }

    /**
     * Registra una transacción contable de doble entrada.
     *
     * <p>Valida el invariante Σ = 0 en el dominio; si los asientos no suman cero, responde con
     * un error {@code ProblemDetail} (Requisito 3, criterios 1 y 2).
     */
    @PostMapping
    @Operation(summary = "Registrar una transacción contable balanceada (Σ = 0)")
    public ResponseEntity<TransactionResponse> register(
            @Valid @RequestBody RegisterTransactionRequest request) {
        LedgerTransaction transaction = ledgerService.register(request);
        TransactionResponse body = TransactionResponse.from(transaction);
        return ResponseEntity.created(URI.create("/transactions/" + body.id())).body(body);
    }

    /**
     * Consulta el saldo derivado de una cuenta en una moneda (por defecto CLP).
     *
     * <p>El saldo se obtiene sumando los asientos de la cuenta; una cuenta sin asientos en la
     * moneda solicitada devuelve cero (Requisito 3, criterio 4).
     */
    @GetMapping("/balances/{accountId}")
    @Operation(summary = "Consultar el saldo derivado de una cuenta en una moneda")
    public BalanceResponse balance(
            @PathVariable UUID accountId,
            @RequestParam(name = "currency", defaultValue = "CLP") Currency currency) {
        AccountBalance balance = balanceService.balance(accountId, currency);
        return BalanceResponse.from(balance);
    }
}
