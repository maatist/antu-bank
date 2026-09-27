package cl.antubank.transfer.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import cl.antubank.transfer.api.TransferController;
import cl.antubank.transfer.persistence.TransferEntity;
import cl.antubank.transfer.persistence.TransferStatus;
import cl.antubank.transfer.service.TransferResult;
import cl.antubank.transfer.service.TransferService;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests de autorización del transfer-service (tarea 8.5, Requisito 8, criterios 4, 5 y 6).
 *
 * <p>Ejercita la <b>cadena de seguridad de producción</b>
 * ({@link SecurityConfig#securityFilterChain}, {@code @Profile("!test")}) activando el perfil
 * {@code prod}. Sustituye el {@link JwtDecoder} por un stub local para acuñar tokens válidos e
 * inválidos sin depender de Keycloak (ver {@link JwtStubConfig}).
 *
 * <ul>
 *   <li><b>Criterio 5 (401 sin token):</b> {@code POST /transfers} sin token responde {@code 401}.</li>
 *   <li><b>Criterio 4 (validación JWT):</b> token válido aceptado; token inválido rechazado con
 *       {@code 401}.</li>
 *   <li><b>Criterio 6 (403 sin rol):</b> {@code POST /transfers} (iniciar transferencia) exige el
 *       rol {@code CUSTOMER}; un JWT válido sin ese rol responde {@code 403}.</li>
 * </ul>
 */
@WebMvcTest(controllers = TransferController.class)
@Import({SecurityConfig.class, AuthorizationSecurityTest.JwtStubConfig.class})
@ActiveProfiles("prod")
class AuthorizationSecurityTest {

    /** Token válido con rol CUSTOMER. */
    static final String TOKEN_CUSTOMER = "valido-customer";
    /** Token válido sin roles. */
    static final String TOKEN_SIN_ROL = "valido-sin-rol";
    /** Token con firma inválida / expirado. */
    static final String TOKEN_INVALIDO = "invalido";

    @Autowired
    MockMvc mvc;

    @MockBean
    TransferService transferService;

    private static final String BODY = """
            {
              "sourceAccountId": "11111111-1111-1111-1111-111111111111",
              "destinationAccountId": "22222222-2222-2222-2222-222222222222",
              "amountMinor": 50000
            }
            """;

    @Test
    void sinTokenResponde401() throws Exception {
        // Criterio 5: endpoint protegido sin token -> 401.
        mvc.perform(post("/transfers")
                        .header(TransferController.IDEMPOTENCY_KEY_HEADER, "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        // Criterio 4: token con firma inválida/expirado -> 401.
        mvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + TOKEN_INVALIDO)
                        .header(TransferController.IDEMPOTENCY_KEY_HEADER, "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinRolCustomerResponde403() throws Exception {
        // Criterio 6: JWT válido sin rol CUSTOMER en POST /transfers -> 403.
        mvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + TOKEN_SIN_ROL)
                        .header(TransferController.IDEMPOTENCY_KEY_HEADER, "key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenConRolCustomerInicaTransferencia() throws Exception {
        // Criterio 4/6 (camino feliz): JWT válido con rol CUSTOMER -> autorizado -> 201.
        UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID destination = UUID.fromString("22222222-2222-2222-2222-222222222222");
        TransferEntity entity = new TransferEntity(
                UUID.randomUUID(), source, destination,
                Money.ofMinor(50000, Currency.CLP), TransferStatus.CONFIRMED);
        Mockito.when(transferService.transfer(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new TransferResult(entity, false));

        mvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + TOKEN_CUSTOMER)
                        .header(TransferController.IDEMPOTENCY_KEY_HEADER, "key-4")
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
