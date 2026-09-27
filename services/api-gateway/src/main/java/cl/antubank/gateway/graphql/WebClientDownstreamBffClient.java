package cl.antubank.gateway.graphql;

import cl.antubank.gateway.graphql.dto.AccountDto;
import cl.antubank.gateway.graphql.dto.BalanceDto;
import cl.antubank.gateway.graphql.dto.TransferDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del {@link DownstreamBffClient} sobre {@link WebClient} reactivo.
 *
 * <p>Cada método propaga el {@code bearerToken} entrante como header {@code Authorization} a la
 * llamada downstream (tarea 9.3, Requisito 9, criterios 2 y 5), de modo que el JWT del usuario
 * autenticado llega a account/ledger/transfer, que lo validan como resource servers.
 *
 * <p>El historial de transferencias consume el endpoint real de transfer-service
 * ({@code GET /transfers?accountId=}, tarea 9.4) por cuenta; ante un fallo del downstream degrada
 * de forma segura a lista vacía (ver {@link DownstreamBffClient#transfersByAccount}).
 */
@Component
public class WebClientDownstreamBffClient implements DownstreamBffClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientDownstreamBffClient.class);

    private final WebClient accountServiceWebClient;
    private final WebClient ledgerServiceWebClient;
    private final WebClient transferServiceWebClient;

    public WebClientDownstreamBffClient(
            @Qualifier("accountServiceWebClient") WebClient accountServiceWebClient,
            @Qualifier("ledgerServiceWebClient") WebClient ledgerServiceWebClient,
            @Qualifier("transferServiceWebClient") WebClient transferServiceWebClient) {
        this.accountServiceWebClient = accountServiceWebClient;
        this.ledgerServiceWebClient = ledgerServiceWebClient;
        this.transferServiceWebClient = transferServiceWebClient;
    }

    @Override
    public Flux<AccountDto> accountsByRut(String rut, String bearerToken) {
        return accountServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/accounts").queryParam("rut", rut).build())
                .headers(headers -> applyBearer(headers, bearerToken))
                .retrieve()
                .bodyToFlux(AccountDto.class);
    }

    @Override
    public Mono<BalanceDto> balance(String accountId, String bearerToken) {
        return ledgerServiceWebClient.get()
                .uri("/transactions/balances/{accountId}", accountId)
                .headers(headers -> applyBearer(headers, bearerToken))
                .retrieve()
                .bodyToMono(BalanceDto.class);
    }

    @Override
    public Flux<TransferDto> transfersByAccount(String accountId, String bearerToken) {
        // Historial real de transfer-service (tarea 9.4): GET /transfers?accountId=. Si el
        // downstream falla (conexión, 5xx, etc.), se degrada a vacío sin romper la agregación
        // (Requisito 9, criterios 4 y 5).
        return transferServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/transfers")
                        .queryParam("accountId", accountId).build())
                .headers(headers -> applyBearer(headers, bearerToken))
                .retrieve()
                .bodyToFlux(TransferDto.class)
                .onErrorResume(ex -> {
                    log.debug("Historial de transferencias no disponible para el BFF "
                            + "(cuenta {}): {}", accountId, ex.toString());
                    return Flux.empty();
                });
    }

    /** Adjunta {@code Authorization: Bearer <token>} si el token está presente. */
    private static void applyBearer(HttpHeaders headers, String bearerToken) {
        if (StringUtils.hasText(bearerToken)) {
            headers.setBearerAuth(bearerToken);
        }
    }
}
