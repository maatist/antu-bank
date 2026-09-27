package cl.antubank.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.domain.money.Currency;
import cl.antubank.ledger.AbstractPostgresIntegrationTest;
import cl.antubank.ledger.domain.EntryType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Tests de integración de la API del ledger contra un PostgreSQL real (Testcontainers).
 *
 * <p>Cubren el Requisito 3: registrar una transacción balanceada (criterio 1), rechazar una
 * desbalanceada (criterio 2) y consultar el saldo derivado de una cuenta (criterio 4), además
 * de la localización es/en de los errores.
 */
class LedgerApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private Map<String, Object> entry(UUID accountId, EntryType type, long amountMinor) {
        return Map.of(
                "accountId", accountId.toString(),
                "type", type.name(),
                "amountMinor", amountMinor);
    }

    private Map<String, Object> transaction(String reference, UUID debit, UUID credit, long amount) {
        return Map.of(
                "reference", reference,
                "currency", Currency.CLP.name(),
                "entries", List.of(
                        entry(debit, EntryType.DEBIT, amount),
                        entry(credit, EntryType.CREDIT, amount)));
    }

    @Test
    void registrarTransaccionBalanceadaRetorna201YSaldoConsistente() {
        UUID cuenta = UUID.randomUUID();
        UUID contraparte = UUID.randomUUID();

        ResponseEntity<Map> create = rest.postForEntity(
                "/transactions", transaction("dep-1", cuenta, contraparte, 30_000), Map.class);

        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(create.getBody()).isNotNull();
        assertThat(create.getBody().get("id")).isNotNull();
        assertThat(create.getBody().get("currency")).isEqualTo("CLP");
        assertThat((List<?>) create.getBody().get("entries")).hasSize(2);

        // El saldo derivado de la cuenta debitada refleja el asiento (+30.000 CLP).
        ResponseEntity<Map> balance = rest.getForEntity(
                "/transactions/balances/" + cuenta, Map.class);
        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balance.getBody().get("currency")).isEqualTo("CLP");
        assertThat(((Number) balance.getBody().get("amountMinor")).longValue()).isEqualTo(30_000L);

        // La contraparte acreditada tiene el saldo espejo (−30.000 CLP).
        ResponseEntity<Map> mirror = rest.getForEntity(
                "/transactions/balances/" + contraparte, Map.class);
        assertThat(((Number) mirror.getBody().get("amountMinor")).longValue()).isEqualTo(-30_000L);
    }

    @Test
    void saldoAcumulaVariasTransacciones() {
        UUID cuenta = UUID.randomUUID();
        UUID contraparte = UUID.randomUUID();

        rest.postForEntity("/transactions", transaction("dep-1", cuenta, contraparte, 30_000), Map.class);
        rest.postForEntity("/transactions", transaction("dep-2", cuenta, contraparte, 20_000), Map.class);
        // Giro: la cuenta ahora es acreditada (sale dinero).
        rest.postForEntity("/transactions", transaction("giro-1", contraparte, cuenta, 12_000), Map.class);

        // Σ = +30.000 +20.000 −12.000 = 38.000 CLP.
        ResponseEntity<Map> balance = rest.getForEntity(
                "/transactions/balances/" + cuenta, Map.class);
        assertThat(((Number) balance.getBody().get("amountMinor")).longValue()).isEqualTo(38_000L);
    }

    @Test
    void saldoDeCuentaSinAsientosEsCero() {
        ResponseEntity<Map> balance = rest.getForEntity(
                "/transactions/balances/" + UUID.randomUUID(), Map.class);

        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) balance.getBody().get("amountMinor")).longValue()).isEqualTo(0L);
        assertThat(balance.getBody().get("currency")).isEqualTo("CLP");
    }

    @Test
    void transaccionDesbalanceadaEsRechazada() {
        // Débito 30.000 contra crédito 25.000: Σ ≠ 0.
        Map<String, Object> body = Map.of(
                "reference", "malo",
                "currency", Currency.CLP.name(),
                "entries", List.of(
                        entry(UUID.randomUUID(), EntryType.DEBIT, 30_000),
                        entry(UUID.randomUUID(), EntryType.CREDIT, 25_000)));

        ResponseEntity<Map> resp = rest.postForEntity("/transactions", body, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("title")).isEqualTo("Transaccion desbalanceada");
    }

    @Test
    void transaccionConUnSoloAsientoEsRechazada() {
        Map<String, Object> body = Map.of(
                "currency", Currency.CLP.name(),
                "entries", List.of(entry(UUID.randomUUID(), EntryType.DEBIT, 30_000)));

        ResponseEntity<Map> resp = rest.postForEntity("/transactions", body, Map.class);

        // Bean Validation (@Size min = 2) rechaza antes de llegar al dominio.
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("title")).isEqualTo("Error de validacion");
    }

    @Test
    void montoNoPositivoEsRechazado() {
        Map<String, Object> body = Map.of(
                "currency", Currency.CLP.name(),
                "entries", List.of(
                        entry(UUID.randomUUID(), EntryType.DEBIT, 0),
                        entry(UUID.randomUUID(), EntryType.CREDIT, 0)));

        ResponseEntity<Map> resp = rest.postForEntity("/transactions", body, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("title")).isEqualTo("Error de validacion");
    }

    @Test
    void errorDesbalanceLocalizadoEnIngles() {
        Map<String, Object> body = Map.of(
                "currency", Currency.CLP.name(),
                "entries", List.of(
                        entry(UUID.randomUUID(), EntryType.DEBIT, 30_000),
                        entry(UUID.randomUUID(), EntryType.CREDIT, 25_000)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "en");

        ResponseEntity<Map> resp = rest.exchange(
                "/transactions", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("title")).isEqualTo("Unbalanced transaction");
    }
}
