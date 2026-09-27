package cl.antubank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cl.antubank.transfer.AbstractPostgresIntegrationTest;
import cl.antubank.transfer.ledger.MockLedgerConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

/**
 * Tests de integración de la API de transferencias contra un PostgreSQL real (Testcontainers) y un
 * ledger-service simulado ({@link MockRestServiceServer}, ver {@link MockLedgerConfig}).
 *
 * <p>Cubren el Requisito 4:
 * <ul>
 *   <li>Criterios 1 y 2 (idempotencia): clave nueva {@code 201}, reintento {@code 200} sin
 *       duplicar el movimiento ni volver a invocar al ledger.</li>
 *   <li>Criterio 4 (CLP): montos en pesos, enviados como minor units al ledger.</li>
 *   <li>Criterio 5 (fondos): con saldo suficiente se confirma y se registra el asiento; con saldo
 *       insuficiente se rechaza ({@code 422}) y no se invoca al ledger.</li>
 * </ul>
 */
@Import(MockLedgerConfig.class)
class TransferApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MockRestServiceServer ledgerMockServer;

    @AfterEach
    void resetLedger() {
        ledgerMockServer.reset();
    }

    private Map<String, Object> transferBody(UUID source, UUID destination, long amountMinor) {
        return Map.of(
                "sourceAccountId", source.toString(),
                "destinationAccountId", destination.toString(),
                "amountMinor", amountMinor);
    }

    private HttpEntity<Map<String, Object>> withKey(Map<String, Object> body, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.add(TransferController.IDEMPOTENCY_KEY_HEADER, key);
        }
        return new HttpEntity<>(body, headers);
    }

    /** Simula un saldo disponible (CLP) para la cuenta origen consultada por el transfer-service. */
    private void expectBalance(UUID accountId, long balanceMinor) {
        ledgerMockServer.expect(ExpectedCount.once(),
                        requestTo(Matchers.containsString(
                                "/transactions/balances/" + accountId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"accountId":"%s","currency":"CLP","amountMinor":%d}
                        """.formatted(accountId, balanceMinor), MediaType.APPLICATION_JSON));
    }

    /** Espera el registro de la doble entrada de la transferencia en el ledger. */
    private void expectRegisterTransfer(UUID source, UUID destination, long amountMinor) {
        ledgerMockServer.expect(ExpectedCount.once(), requestTo(Matchers.endsWith("/transactions")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.currency").value("CLP"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].accountId").value(source.toString()))
                .andExpect(jsonPath("$.entries[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].amountMinor").value(amountMinor))
                .andExpect(jsonPath("$.entries[1].accountId").value(destination.toString()))
                .andExpect(jsonPath("$.entries[1].type").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].amountMinor").value(amountMinor))
                .andRespond(withSuccess("""
                        {"id":"%s","currency":"CLP","entries":[]}
                        """.formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));
    }

    @Test
    void fondosSuficientesConfirmaYRegistraAsientoEnLedger() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 50_000;
        expectBalance(source, 100_000);
        expectRegisterTransfer(source, destination, amount);

        Map<String, Object> body = transferBody(source, destination, amount);
        ResponseEntity<Map> resp = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, "key-fondos-ok"), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("status")).isEqualTo("CONFIRMED");
        assertThat(resp.getBody().get("currency")).isEqualTo("CLP");
        assertThat(((Number) resp.getBody().get("amountMinor")).longValue()).isEqualTo(amount);
        // Se consultó el saldo y se registró el asiento en el ledger.
        ledgerMockServer.verify();
    }

    @Test
    void fondosExactosAlSaldoSePermite() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 30_000;
        // Saldo exactamente igual al monto: no deja saldo negativo, se permite.
        expectBalance(source, amount);
        expectRegisterTransfer(source, destination, amount);

        Map<String, Object> body = transferBody(source, destination, amount);
        ResponseEntity<Map> resp = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, "key-fondos-exactos"), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ledgerMockServer.verify();
    }

    @Test
    void fondosInsuficientesRechazaSinRegistrarAsiento() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        // Saldo menor al monto solicitado: se consulta el saldo pero NO se registra asiento.
        expectBalance(source, 10_000);

        Map<String, Object> body = transferBody(source, destination, 50_000);
        ResponseEntity<Map> resp = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, "key-sin-fondos"), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("title")).isEqualTo("Fondos insuficientes");
        // Solo se consultó el saldo; no hubo POST /transactions (verify cubre el conteo esperado).
        ledgerMockServer.verify();
    }

    @Test
    void mismaClaveRetornaMismoResultadoSinReinvocarLedger() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 75_000;
        String key = "key-repetida-1";
        // El ledger se invoca UNA sola vez (once): la reproducción no debe volver a llamarlo.
        expectBalance(source, 200_000);
        expectRegisterTransfer(source, destination, amount);

        Map<String, Object> body = transferBody(source, destination, amount);
        ResponseEntity<Map> first = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, key), Map.class);
        ResponseEntity<Map> second = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, key), Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("id")).isEqualTo(first.getBody().get("id"));
        assertThat(((Number) second.getBody().get("amountMinor")).longValue()).isEqualTo(amount);
        // Verifica que cada llamada al ledger ocurrió exactamente una vez (no re-invocación).
        ledgerMockServer.verify();
    }

    @Test
    void clavesDistintasProducenTransferenciasDistintas() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        long amount = 10_000;
        // Dos claves distintas => dos flujos completos contra el ledger.
        expectBalance(source, 500_000);
        expectRegisterTransfer(source, destination, amount);
        expectBalance(source, 500_000);
        expectRegisterTransfer(source, destination, amount);

        Map<String, Object> body = transferBody(source, destination, amount);
        ResponseEntity<Map> a = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, "key-a"), Map.class);
        ResponseEntity<Map> b = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, "key-b"), Map.class);

        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(b.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(b.getBody().get("id")).isNotEqualTo(a.getBody().get("id"));
        ledgerMockServer.verify();
    }

    @Test
    void faltaHeaderIdempotencyKeyEsRechazado() {
        // Falla antes de tocar el ledger: no se fija ninguna expectativa.
        Map<String, Object> body = transferBody(UUID.randomUUID(), UUID.randomUUID(), 50_000);

        ResponseEntity<Map> resp = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, null), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("title")).isEqualTo("Idempotency-Key requerida");
    }

    @Test
    void headerIdempotencyKeyEnBlancoEsRechazado() {
        Map<String, Object> body = transferBody(UUID.randomUUID(), UUID.randomUUID(), 50_000);

        ResponseEntity<Map> resp = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, "   "), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("title")).isEqualTo("Idempotency-Key requerida");
    }

    @Test
    void cuerpoInvalidoEsRechazadoConErrorDeValidacion() {
        // Falta el monto y las cuentas: Bean Validation rechaza antes de tocar el servicio/ledger.
        Map<String, Object> body = Map.of("amountMinor", -1);

        ResponseEntity<Map> resp = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, "key-invalida"), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("title")).isEqualTo("Error de validacion");
    }

    @Test
    void errorFaltaClaveLocalizadoEnIngles() {
        Map<String, Object> body = transferBody(UUID.randomUUID(), UUID.randomUUID(), 50_000);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "en");

        ResponseEntity<Map> resp = rest.exchange(
                "/transfers", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("title")).isEqualTo("Idempotency-Key required");
    }

    // -------------------------------------------------------------------------
    // Historial de transferencias (tarea 9.4): GET /transfers?accountId=. Alimenta el BFF GraphQL
    // (me { transfers }, Requisito 9, criterio 5). Retorna las transferencias en las que la cuenta
    // participa como origen o destino, de la más reciente a la más antigua.
    // -------------------------------------------------------------------------

    @Test
    void historialIncluyeTransferenciasComoOrigenYDestinoOrdenadasDescendente() {
        UUID cuenta = UUID.randomUUID();
        UUID otra = UUID.randomUUID();
        // Movimiento 1: la cuenta es ORIGEN (envía). Movimiento 2: la cuenta es DESTINO (recibe).
        expectBalance(cuenta, 500_000);
        expectRegisterTransfer(cuenta, otra, 20_000);
        expectBalance(otra, 500_000);
        expectRegisterTransfer(otra, cuenta, 35_000);

        String enviadoId = crearTransferencia(cuenta, otra, 20_000, "hist-envia");
        String recibidoId = crearTransferencia(otra, cuenta, 35_000, "hist-recibe");
        ledgerMockServer.verify();

        ResponseEntity<List> resp = rest.exchange(
                "/transfers?accountId=" + cuenta, HttpMethod.GET, HttpEntity.EMPTY, List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull().hasSize(2);
        // Ambos movimientos aparecen porque la cuenta participa como origen y como destino.
        assertThat(resp.getBody())
                .extracting(m -> ((Map<?, ?>) m).get("id"))
                .containsExactlyInAnyOrder(enviadoId, recibidoId);
        // El más reciente (el recibido, creado después) va primero (orden descendente).
        assertThat(((Map<?, ?>) resp.getBody().get(0)).get("id")).isEqualTo(recibidoId);
        assertThat(((Map<?, ?>) resp.getBody().get(0)).get("currency")).isEqualTo("CLP");
    }

    @Test
    void historialDeCuentaSinMovimientosEsListaVacia() {
        ResponseEntity<List> resp = rest.exchange(
                "/transfers?accountId=" + UUID.randomUUID(), HttpMethod.GET,
                HttpEntity.EMPTY, List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull().isEmpty();
    }

    @Test
    void historialConAccountIdInvalidoEsRechazado() {
        ResponseEntity<Map> resp = rest.exchange(
                "/transfers?accountId=no-es-uuid", HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("title")).isEqualTo("Error de validacion");
    }

    /** Crea una transferencia confirmada y retorna su id (como String) para asertar el historial. */
    private String crearTransferencia(UUID source, UUID destination, long amountMinor, String key) {
        Map<String, Object> body = transferBody(source, destination, amountMinor);
        ResponseEntity<Map> resp = rest.exchange(
                "/transfers", HttpMethod.POST, withKey(body, key), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }
}
