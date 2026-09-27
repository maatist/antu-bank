package cl.antubank.ledger.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.ledger.api.LedgerController;
import cl.antubank.ledger.domain.LedgerEntry;
import cl.antubank.ledger.domain.LedgerTransaction;
import cl.antubank.ledger.service.AccountBalance;
import cl.antubank.ledger.service.BalanceService;
import cl.antubank.ledger.service.LedgerService;
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
 * Tests de autorización del ledger-service (tarea 8.5, Requisito 8, criterios 4, 5 y 6).
 *
 * <p>Ejercita la <b>cadena de seguridad de producción</b>
 * ({@link SecurityConfig#securityFilterChain}, {@code @Profile("!test")}) activando el perfil
 * {@code prod}. Sustituye el {@link JwtDecoder} por un stub local para acuñar tokens válidos e
 * inválidos sin depender de Keycloak (ver {@link JwtStubConfig}).
 *
 * <ul>
 *   <li><b>Criterio 5 (401 sin token):</b> un endpoint protegido sin token responde {@code 401}.</li>
 *   <li><b>Criterio 4 (validación JWT):</b> token válido aceptado; token inválido rechazado con
 *       {@code 401}.</li>
 *   <li><b>Criterio 6 (403 sin rol):</b> {@code POST /transactions} (registro de asiento) exige el
 *       rol {@code ADMIN}; un JWT válido sin ese rol responde {@code 403}.</li>
 * </ul>
 */
@WebMvcTest(controllers = LedgerController.class)
@Import({SecurityConfig.class, AuthorizationSecurityTest.JwtStubConfig.class})
@ActiveProfiles("prod")
class AuthorizationSecurityTest {

    /** Token válido con rol ADMIN. */
    static final String TOKEN_ADMIN = "valido-admin";
    /** Token válido sin roles. */
    static final String TOKEN_SIN_ROL = "valido-sin-rol";
    /** Token con firma inválida / expirado. */
    static final String TOKEN_INVALIDO = "invalido";

    @Autowired
    MockMvc mvc;

    @MockBean
    LedgerService ledgerService;

    @MockBean
    BalanceService balanceService;

    private static final String BODY = """
            {
              "reference": "TX-001",
              "currency": "CLP",
              "entries": [
                {"accountId": "11111111-1111-1111-1111-111111111111", "type": "DEBIT",
                 "amountMinor": 1000},
                {"accountId": "22222222-2222-2222-2222-222222222222", "type": "CREDIT",
                 "amountMinor": 1000}
              ]
            }
            """;

    @Test
    void sinTokenResponde401() throws Exception {
        // Criterio 5: endpoint protegido sin token -> 401.
        mvc.perform(get("/transactions/balances/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        // Criterio 4: token con firma inválida/expirado -> 401.
        mvc.perform(get("/transactions/balances/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN_INVALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoEsAceptado() throws Exception {
        // Criterio 4: token válido -> aceptado (consulta de saldo solo exige autenticación).
        UUID accountId = UUID.randomUUID();
        when(balanceService.balance(any(), any()))
                .thenReturn(new AccountBalance(accountId, Money.ofMinor(0, Currency.CLP)));
        mvc.perform(get("/transactions/balances/{id}", accountId)
                        .header("Authorization", "Bearer " + TOKEN_SIN_ROL))
                .andExpect(status().isOk());
    }

    @Test
    void tokenSinRolAdminResponde403EnRegistrarAsiento() throws Exception {
        // Criterio 6: JWT válido sin rol ADMIN en POST /transactions -> 403.
        mvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + TOKEN_SIN_ROL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenConRolAdminRegistraAsiento() throws Exception {
        // Criterio 6 (camino feliz): JWT válido con rol ADMIN -> autorizado -> 201.
        UUID debit = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID credit = UUID.fromString("22222222-2222-2222-2222-222222222222");
        LedgerTransaction tx = LedgerTransaction.of("TX-001", List.of(
                LedgerEntry.debit(debit, Money.ofMinor(1000, Currency.CLP)),
                LedgerEntry.credit(credit, Money.ofMinor(1000, Currency.CLP))));
        when(ledgerService.register(any())).thenReturn(tx);

        mvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    /**
     * Stub de {@link JwtDecoder}: acuña tokens válidos (con y sin rol) y rechaza el inválido,
     * ejercitando la cadena de seguridad real sin depender de un Keycloak en ejecución.
     */
    @TestConfiguration
    static class JwtStubConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case TOKEN_ADMIN -> jwtConRoles(token, List.of("ADMIN"));
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
