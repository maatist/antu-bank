package cl.antubank.gateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.gateway.graphql.dto.AccountDto;
import cl.antubank.gateway.graphql.dto.BalanceDto;
import cl.antubank.gateway.graphql.dto.TransferDto;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Verifica la propagación del token al downstream (tarea 9.3, Requisito 9, criterios 2 y 5): cada
 * llamada del BFF debe adjuntar {@code Authorization: Bearer <JWT>} y la URL/consulta esperada.
 *
 * <p>Se usa un {@link ExchangeFunction} que captura la petición saliente y responde un cuerpo JSON
 * fijo, sin red ni servicios reales.
 */
class WebClientDownstreamBffClientTest {

    private final List<ClientRequest> captured = new ArrayList<>();

    private WebClient clientRespondingWith(String jsonBody) {
        ExchangeFunction exchange = request -> {
            captured.add(request);
            ClientResponse response = ClientResponse.create(org.springframework.http.HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(jsonBody)
                    .build();
            return Mono.just(response);
        };
        return WebClient.builder().baseUrl("http://downstream").exchangeFunction(exchange).build();
    }

    @Test
    void accountsByRutPropagaTokenYConsultaPorRut() {
        WebClient accounts = clientRespondingWith("[]");
        WebClient ledger = clientRespondingWith("{}");
        WebClient transfer = clientRespondingWith("[]");
        WebClientDownstreamBffClient bff =
                new WebClientDownstreamBffClient(accounts, ledger, transfer);

        List<AccountDto> result = bff.accountsByRut("12.345.678-5", "token-abc")
                .collectList().block();

        assertThat(result).isNotNull().isEmpty();
        ClientRequest request = captured.get(0);
        assertThat(request.url().toString()).contains("/accounts").contains("rut=");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer token-abc");
    }

    @Test
    void balancePropagaTokenYUsaPathDelLedger() {
        WebClient ledger = clientRespondingWith(
                "{\"accountId\":\"a1\",\"currency\":\"CLP\",\"amountMinor\":38000,\"amount\":38000}");
        WebClientDownstreamBffClient bff = new WebClientDownstreamBffClient(
                clientRespondingWith("[]"), ledger, clientRespondingWith("[]"));

        BalanceDto balance = bff.balance("a1", "token-xyz").block();

        assertThat(balance).isNotNull();
        assertThat(balance.amountMinor()).isEqualTo(38000L);
        ClientRequest request = captured.get(0);
        assertThat(request.url().toString()).contains("/transactions/balances/a1");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer token-xyz");
    }

    @Test
    void transfersByAccountPropagaTokenYConsultaPorAccountId() {
        // Historial real (tarea 9.4): GET /transfers?accountId= con Bearer propagado.
        WebClient transfer = clientRespondingWith("""
                [{"id":"t1","status":"CONFIRMED","sourceAccountId":"a1",
                  "destinationAccountId":"a2","amountMinor":50000,"currency":"CLP",
                  "createdAt":"2024-01-15T10:30:00Z"}]
                """);
        WebClientDownstreamBffClient bff = new WebClientDownstreamBffClient(
                clientRespondingWith("[]"), clientRespondingWith("{}"), transfer);

        List<TransferDto> result = bff.transfersByAccount("a1", "token-hist")
                .collectList().block();

        assertThat(result).isNotNull().hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("t1");
        assertThat(result.get(0).amountMinor()).isEqualTo(50000L);
        ClientRequest request = captured.get(0);
        assertThat(request.url().toString()).contains("/transfers").contains("accountId=a1");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer token-hist");
    }

    @Test
    void transfersByAccountDegradaAVacioAnteErrorDelDownstream() {
        // Cliente que responde 500: el historial debe degradar a vacío sin propagar el error.
        ExchangeFunction failing = request -> {
            captured.add(request);
            return Mono.just(ClientResponse.create(
                            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("boom".getBytes(StandardCharsets.UTF_8).length + "")
                    .build());
        };
        WebClient transfer = WebClient.builder()
                .baseUrl("http://downstream").exchangeFunction(failing).build();
        WebClientDownstreamBffClient bff = new WebClientDownstreamBffClient(
                clientRespondingWith("[]"), clientRespondingWith("{}"), transfer);

        var result = bff.transfersByAccount("a1", "t").collectList().block();

        assertThat(result).isNotNull().isEmpty();
    }
}
