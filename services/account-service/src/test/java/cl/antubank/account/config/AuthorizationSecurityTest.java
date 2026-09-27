package cl.antubank.account.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.antubank.account.api.AccountController;
import cl.antubank.account.api.AccountResponse;
import cl.antubank.account.service.AccountService;
import cl.antubank.domain.account.AccountType;
import cl.antubank.domain.account.ChileanBank;
import cl.antubank.domain.money.Currency;
import cl.antubank.account.persistence.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests de autorización del account-service (tarea 8.5, Requisito 8, criterios 4, 5 y 6).
 *
 * <p>A diferencia de los tests de integración de la API (que corren bajo el perfil {@code test}
 * con la cadena permisiva {@code permitAll}), esta clase ejercita la <b>cadena de seguridad de
 * producción</b> ({@link SecurityConfig#securityFilterChain}, anotada {@code @Profile("!test")}).
 * Para ello se declara explícitamente el perfil {@code prod} (cualquier perfil distinto de
 * {@code test} activa la cadena real).
 *
 * <p>Se sustituye el {@link JwtDecoder} por un stub local ({@link JwtStubConfig}) para no depender
 * de un Keycloak en ejecución: así se pueden acuñar tokens válidos (con/ sin rol) e inválidos y
 * verificar la validación de firma/expiración de forma determinista. El stub alimenta la cadena
 * real, de modo que también se ejercita el mapeo de roles de Keycloak
 * ({@link SecurityConfig.KeycloakRealmRoleConverter}).
 *
 * <ul>
 *   <li><b>Criterio 5 (401 sin token):</b> un endpoint protegido sin cabecera {@code Authorization}
 *       responde {@code 401}.</li>
 *   <li><b>Criterio 4 (validación JWT):</b> un token válido es aceptado y uno inválido/expirado es
 *       rechazado con {@code 401}.</li>
 *   <li><b>Criterio 6 (403 sin rol):</b> {@code POST /accounts} exige el rol {@code CUSTOMER}; un
 *       JWT válido sin ese rol responde {@code 403}, y con el rol responde {@code 2xx}.</li>
 * </ul>
 */
@WebMvcTest(controllers = AccountController.class)
@Import({SecurityConfig.class, AuthorizationSecurityTest.JwtStubConfig.class})
@ActiveProfiles("prod")
class AuthorizationSecurityTest {

    /** Token válido con rol CUSTOMER. */
    static final String TOKEN_CUSTOMER = "valido-customer";
    /** Token válido sin roles (autenticado pero sin autorización de rol). */
    static final String TOKEN_SIN_ROL = "valido-sin-rol";
    /** Token con firma inválida / expirado. */
    static final String TOKEN_INVALIDO = "invalido";

    @Autowired
    MockMvc mvc;

    @MockBean
    AccountService accountService;

    private static final String BODY = """
            {
              "holderRut": "12.345.678-5",
              "holderName": "María González",
              "accountType": "CORRIENTE",
              "bank": "BANCO_ESTADO",
              "currency": "CLP"
            }
            """;

    private void stubServicioCreacion() {
        AccountResponse resp = new AccountResponse(
                UUID.randomUUID(), "12.345.678-5", "María González",
                AccountType.CORRIENTE, ChileanBank.BANCO_ESTADO, "BancoEstado",
                Currency.CLP, AccountStatus.ACTIVE, Instant.now());
        when(accountService.create(any())).thenReturn(resp);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        // Criterio 5: endpoint protegido sin token -> 401.
        mvc.perform(get("/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        // Criterio 4: token con firma inválida/expirado -> 401.
        mvc.perform(get("/accounts/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN_INVALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoEsAceptado() throws Exception {
        // Criterio 4: token válido -> aceptado (no 401/403). GET solo exige autenticación.
        when(accountService.findByHolderRut(any())).thenReturn(List.of());
        mvc.perform(get("/accounts").param("rut", "12.345.678-5")
                        .header("Authorization", "Bearer " + TOKEN_CUSTOMER))
                .andExpect(status().isOk());
    }

    @Test
    void tokenSinRolCustomerResponde403EnCrearCuenta() throws Exception {
        // Criterio 6: JWT válido sin rol CUSTOMER en POST /accounts -> 403.
        mvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + TOKEN_SIN_ROL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenConRolCustomerCreaCuenta() throws Exception {
        // Criterio 6 (camino feliz): JWT válido con rol CUSTOMER -> 201.
        stubServicioCreacion();
        mvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + TOKEN_CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    /**
     * Stub de {@link JwtDecoder} que evita depender de Keycloak: acuña tokens válidos (con y sin
     * rol) y rechaza el token inválido, ejercitando la cadena de seguridad real.
     */
    @TestConfiguration
    static class JwtStubConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case TOKEN_CUSTOMER -> jwtConRoles(token, List.of("CUSTOMER"));
                case TOKEN_SIN_ROL -> jwtConRoles(token, List.of());
                // BadJwtException representa un token inválido/expirado (firma incorrecta, etc.)
                // que el resource server traduce a 401 (invalid_token), no a un 500.
                default -> throw new BadJwtException("Token inválido o expirado");
            };
        }

        private static Jwt jwtConRoles(String tokenValue, List<String> roles) {
            return Jwt.withTokenValue(tokenValue)
                    .header("alg", "RS256")
                    .subject("usuario-de-prueba")
                    .claim("preferred_username", "usuario-de-prueba")
                    .claim("realm_access", Map.of("roles", roles))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
        }
    }
}
