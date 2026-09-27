package cl.antubank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.account.AbstractPostgresIntegrationTest;
import cl.antubank.domain.account.AccountType;
import cl.antubank.domain.account.ChileanBank;
import cl.antubank.domain.money.Currency;
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
 * Tests de integración de la API de cuentas contra un PostgreSQL real (Testcontainers).
 */
class AccountApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private static final String RUT_VALIDO = "12.345.678-5";

    private Map<String, Object> validRequestBody() {
        return Map.of(
                "holderRut", RUT_VALIDO,
                "holderName", "María González",
                "accountType", AccountType.CORRIENTE.name(),
                "bank", ChileanBank.BANCO_ESTADO.name(),
                "currency", Currency.CLP.name()
        );
    }

    @Test
    void crearYConsultarCuenta() {
        // Crear
        ResponseEntity<Map> create = rest.postForEntity("/accounts", validRequestBody(), Map.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(create.getBody()).isNotNull();
        assertThat(create.getBody().get("holderRut")).isEqualTo("12.345.678-5");
        assertThat(create.getBody().get("bankName")).isEqualTo("BancoEstado");
        assertThat(create.getBody().get("status")).isEqualTo("ACTIVE");

        String id = create.getBody().get("id").toString();

        // Consultar por id
        ResponseEntity<Map> get = rest.getForEntity("/accounts/" + id, Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get.getBody().get("holderName")).isEqualTo("María González");
    }

    @Test
    void listarPorRut() {
        rest.postForEntity("/accounts", validRequestBody(), Map.class);

        ResponseEntity<Object[]> list = rest.getForEntity("/accounts?rut=" + RUT_VALIDO, Object[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).isNotEmpty();
    }

    @Test
    void rutInvalidoRetorna400() {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("holderRut", "12.345.678-9"); // DV incorrecto

        ResponseEntity<Map> resp = rest.postForEntity("/accounts", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("title")).isEqualTo("Error de validacion");
    }

    @Test
    void cuentaInexistenteRetorna404() {
        ResponseEntity<Map> resp = rest.getForEntity("/accounts/" + UUID.randomUUID(), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().get("title")).isEqualTo("Cuenta no encontrada");
    }

    @Test
    void erroresLocalizadosEnIngles() {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("holderRut", "12.345.678-9");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "en");

        ResponseEntity<Map> resp = rest.exchange(
                "/accounts", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("title")).isEqualTo("Validation error");
    }
}
