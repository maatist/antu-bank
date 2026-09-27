package cl.antubank.gateway.graphql;

import cl.antubank.gateway.graphql.dto.AccountDto;
import cl.antubank.gateway.graphql.dto.BalanceDto;
import cl.antubank.gateway.graphql.dto.TransferDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fachada reactiva sobre los REST internos que agrega el BFF GraphQL (tarea 9.3, Requisito 9,
 * criterio 5). Aísla las llamadas HTTP a account/ledger/transfer para que la lógica de agregación
 * del controlador GraphQL sea testeable mockeando este cliente (sin levantar los servicios).
 *
 * <p>Todas las operaciones reciben el {@code bearerToken} entrante y lo propagan como header
 * {@code Authorization} a la llamada downstream.
 */
public interface DownstreamBffClient {

    /** Lista las cuentas del titular por RUT (account-service GET /accounts?rut=). */
    Flux<AccountDto> accountsByRut(String rut, String bearerToken);

    /**
     * Saldo derivado de una cuenta (ledger-service GET /transactions/balances/{accountId}).
     * La moneda por defecto del servicio es CLP.
     */
    Mono<BalanceDto> balance(String accountId, String bearerToken);

    /**
     * Historial de transferencias de una cuenta (transfer-service
     * {@code GET /transfers?accountId=}): movimientos en los que la cuenta participa como origen o
     * destino, de la más reciente a la más antigua (tarea 9.4).
     *
     * <p>El historial es por cuenta porque transfer-service opera con identificadores de cuenta, no
     * con RUT. El resolver de {@code me} consulta este endpoint por cada cuenta del titular y
     * consolida el resultado. Ante un fallo del downstream, la implementación degrada de forma
     * segura devolviendo una lista vacía para no romper la agregación de cuentas y saldos
     * (Requisito 9, criterios 4 y 5).
     *
     * @param accountId   cuenta cuyo historial se consulta.
     * @param bearerToken JWT entrante a propagar como {@code Authorization: Bearer}.
     */
    Flux<TransferDto> transfersByAccount(String accountId, String bearerToken);
}
