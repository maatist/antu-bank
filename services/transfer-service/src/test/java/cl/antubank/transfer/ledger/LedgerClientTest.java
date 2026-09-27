package cl.antubank.transfer.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests unitarios del {@link LedgerClient} contra un ledger simulado ({@link MockRestServiceServer}).
 *
 * <p>Verifican que el cliente construye correctamente las llamadas HTTP del contrato del ledger:
 * la consulta de saldo (GET) y el registro de la doble entrada de una transferencia (POST con
 * débito origen + crédito destino en CLP).
 */
class LedgerClientTest {

    private MockRestServiceServer server;
    private LedgerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ledger.test");
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.client = new LedgerClient(builder.build());
    }

    @Test
    void balanceConsultaElSaldoDerivadoYLoDevuelveComoMoney() {
        UUID accountId = UUID.randomUUID();
        server.expect(requestTo(Matchers.containsString(
                        "/transactions/balances/" + accountId + "?currency=CLP")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"accountId":"%s","currency":"CLP","amountMinor":38000}
                        """.formatted(accountId), MediaType.APPLICATION_JSON));

        Money balance = client.balance(accountId, Currency.CLP);

        assertThat(balance).isEqualTo(Money.ofMinor(38_000, Currency.CLP));
        server.verify();
    }

    @Test
    void registerTransferEnviaDobleEntradaBalanceadaEnClp() {
        UUID transferId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();

        server.expect(requestTo(Matchers.endsWith("/transactions")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.reference").value("transfer-" + transferId))
                .andExpect(jsonPath("$.currency").value("CLP"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].accountId").value(source.toString()))
                .andExpect(jsonPath("$.entries[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amountMinor").value(50_000))
                .andExpect(jsonPath("$.entries[1].accountId").value(destination.toString()))
                .andExpect(jsonPath("$.entries[1].type").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amountMinor").value(50_000))
                .andRespond(withSuccess("""
                        {"id":"%s","currency":"CLP","entries":[]}
                        """.formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));

        client.registerTransfer(transferId, source, destination,
                Money.ofMinor(50_000, Currency.CLP));

        server.verify();
    }
}
