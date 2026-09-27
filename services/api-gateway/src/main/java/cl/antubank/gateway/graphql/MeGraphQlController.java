package cl.antubank.gateway.graphql;

import cl.antubank.gateway.graphql.model.AccountModel;
import cl.antubank.gateway.graphql.model.MeModel;
import cl.antubank.gateway.graphql.model.MoneyModel;
import cl.antubank.gateway.graphql.model.TransferModel;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Controlador del BFF GraphQL (tarea 9.3, Requisito 9, criterios 5 y 6; design.md 5.5, ADR-008).
 *
 * <p>Resuelve la query {@code me}, que agrega en una sola respuesta los REST internos:
 * <ol>
 *   <li><b>account-service</b> {@code GET /accounts?rut=} — cuentas del titular.</li>
 *   <li><b>ledger-service</b> {@code GET /transactions/balances/{accountId}} — saldo por cuenta,
 *       resuelto de forma anidada por {@link #balance(AccountModel)} (campo {@code Account.balance}).</li>
 *   <li><b>transfer-service</b> — historial de transferencias del cliente.</li>
 * </ol>
 *
 * <p><b>Identidad del cliente.</b> Los tokens demo de Keycloak no mapean el atributo {@code rut}
 * como claim (ver realm-export.json), por lo que {@code me} acepta un argumento {@code rut} para la
 * demo. La resolución es: (1) argumento {@code rut} si viene; (2) claim {@code rut} del JWT si
 * existiera; en otro caso se rechaza con un error claro. El RUT elegido se usa para consultar los
 * servicios internos.
 *
 * <p><b>Propagación de token.</b> El JWT validado en el borde se obtiene del contexto de seguridad
 * reactivo ({@link ReactiveSecurityContextHolder}); su valor crudo ({@link Jwt#getTokenValue()}) se
 * propaga como {@code Authorization: Bearer} en cada llamada downstream (a través de
 * {@link DownstreamBffClient}). La autenticación se exige en el borde (SecurityConfig: {@code
 * /graphql} requiere JWT); aquí se lee de forma tolerante para no acoplar la lógica de agregación
 * a la infraestructura de seguridad.
 *
 * <p>Todo es reactivo (Mono/Flux): las cuentas y el historial se cargan en paralelo y el saldo de
 * cada cuenta se resuelve bajo demanda por el resolver del campo anidado.
 */
@Controller
public class MeGraphQlController {

    private final DownstreamBffClient client;

    public MeGraphQlController(DownstreamBffClient client) {
        this.client = client;
    }

    /**
     * Resolver de la query raíz {@code me}: arma el agregado del cliente.
     *
     * @param rut argumento opcional con el RUT del cliente (necesario en la demo, ver clase).
     */
    @QueryMapping
    public Mono<MeModel> me(@Argument @Nullable String rut) {
        return currentJwt().flatMap(jwtHolder -> {
            Jwt jwt = jwtHolder.value();
            String resolvedRut = resolveRut(rut, jwt);
            String bearerToken = jwt != null ? jwt.getTokenValue() : null;

            // Las cuentas del titular se cargan primero: el historial es por cuenta (transfer-service
            // opera con identificadores de cuenta, no con RUT), así que se derivan de ellas.
            return client.accountsByRut(resolvedRut, bearerToken)
                    .map(dto -> AccountModel.from(dto, bearerToken))
                    .collectList()
                    .flatMap(accounts -> transfersFor(accounts, bearerToken)
                            .map(transfers -> new MeModel(resolvedRut, accounts, transfers)));
        });
    }

    /**
     * Consolida el historial de transferencias de todas las cuentas del titular (tarea 9.4). Consulta
     * el endpoint real de transfer-service por cada cuenta, deduplica por id de transferencia (una
     * misma transferencia entre dos cuentas propias aparece en ambos historiales) y ordena por fecha
     * de creación descendente (más recientes primero). Ante un fallo del downstream, cada llamada
     * degrada a vacío en el cliente, por lo que la agregación nunca se rompe (Requisito 9,
     * criterios 4 y 5).
     */
    private Mono<java.util.List<TransferModel>> transfersFor(
            java.util.List<AccountModel> accounts, @Nullable String bearerToken) {
        return reactor.core.publisher.Flux.fromIterable(accounts)
                .flatMap(account -> client.transfersByAccount(account.id(), bearerToken))
                .map(TransferModel::from)
                .collect(java.util.stream.Collectors.toMap(
                        TransferModel::id, m -> m, (a, b) -> a, java.util.LinkedHashMap::new))
                .map(byId -> byId.values().stream()
                        .sorted(java.util.Comparator.comparing(
                                TransferModel::createdAt,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                        .toList());
    }

    /**
     * Obtiene el JWT actual del contexto de seguridad reactivo de forma tolerante: si no hay
     * autenticación (p. ej. en pruebas de la lógica de agregación), emite un holder con {@code null}
     * en lugar de fallar. La exigencia de autenticación vive en el borde (SecurityConfig).
     */
    private static Mono<JwtHolder> currentJwt() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication() != null
                        ? ctx.getAuthentication().getPrincipal() : null)
                .map(principal -> principal instanceof Jwt jwt ? new JwtHolder(jwt)
                        : new JwtHolder(null))
                .defaultIfEmpty(new JwtHolder(null));
    }

    /** Envoltorio para poder transportar un JWT posiblemente nulo dentro del pipeline reactivo. */
    private record JwtHolder(@Nullable Jwt value) {
    }

    /**
     * Resolver del campo anidado {@code Account.balance}: consulta el saldo derivado de la cuenta
     * en ledger-service, propagando el token que la cuenta transporta.
     */
    @SchemaMapping(typeName = "Account", field = "balance")
    public Mono<MoneyModel> balance(AccountModel account) {
        return client.balance(account.id(), account.bearerToken())
                .map(MoneyModel::from);
    }

    /**
     * Determina el RUT del cliente: argumento explícito, si no el claim {@code rut} del JWT, si no
     * error. No se usa {@code sub}/{@code preferred_username} como RUT porque en este realm el
     * username no es el RUT del titular de las cuentas.
     */
    private static String resolveRut(@Nullable String rutArgument, @Nullable Jwt jwt) {
        if (StringUtils.hasText(rutArgument)) {
            return rutArgument;
        }
        if (jwt != null) {
            String claim = jwt.getClaimAsString("rut");
            if (StringUtils.hasText(claim)) {
                return claim;
            }
        }
        throw new IllegalArgumentException(
                "No se pudo determinar el RUT del cliente: el token no incluye el claim 'rut'. "
                        + "Entregue el argumento 'rut' en la query me(rut: \"...\").");
    }
}
