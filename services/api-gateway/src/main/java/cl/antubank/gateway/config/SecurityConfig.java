package cl.antubank.gateway.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Seguridad del api-gateway como <b>OAuth2 Resource Server reactivo</b> en el borde
 * (tarea 9.1, Requisito 9, criterio 2; design.md sección 7).
 *
 * <p>El gateway corre sobre Spring WebFlux, por lo que la seguridad es <b>reactiva</b>: se declara
 * un {@link SecurityWebFilterChain} (no el {@code SecurityFilterChain} servlet). El gateway valida
 * la firma del JWT contra el <b>JWKS</b> del realm {@code antu-bank} de Keycloak (validación local
 * tras cachear las claves) y, una vez validado, <b>propaga el header {@code Authorization}</b> a
 * los servicios downstream. Spring Cloud Gateway reenvía por defecto los headers de la petición
 * entrante (incluido {@code Authorization}); no se elimina ese header en ninguna ruta, de modo que
 * el Bearer token llega intacto a account/ledger/transfer, que a su vez lo validan como resource
 * servers (tarea 8.2).
 *
 * <p>Autorización de rutas:
 * <ul>
 *   <li>Se <b>permiten</b> sin autenticación: health/info de actuator, las rutas de documentación,
 *       la UI del playground GraphiQL ({@code /graphiql/**}, tarea 9.3, Requisito 9, criterio 6) y
 *       las rutas de fallback del circuit breaker ({@code /fallback/**}, tarea 9.2).</li>
 *   <li>El endpoint {@code /graphql} <b>exige JWT</b>: la query {@code me} expone datos del usuario
 *       (cuentas, saldos, historial), por lo que no puede ser anónima. La UI GraphiQL carga sin
 *       token, pero para ejecutar queries el usuario debe adjuntar {@code Authorization: Bearer};
 *       ese token se propaga a los servicios internos que agrega el BFF (tarea 9.3, criterio 5).</li>
 *   <li>Las rutas de negocio ({@code /api/**}) exigen un JWT válido: sin token la respuesta es
 *       {@code 401} (alineado con Requisito 8, criterio 5).</li>
 * </ul>
 *
 * <p>El {@code issuer-uri} se configura en {@code application.yml} (variable
 * {@code KEYCLOAK_ISSUER_URI}); el {@code ReactiveJwtDecoder} lo autoconfigura Spring Boot.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Rutas abiertas (sin autenticación): infraestructura, documentación y la UI del playground
     * GraphiQL (tarea 9.3, Requisito 9, criterio 6). Nótese que {@code /graphql} NO está aquí: el
     * endpoint de queries exige JWT (ver {@link #securityWebFilterChain}), porque {@code me} expone
     * datos del usuario. La UI GraphiQL sí es pública para poder abrirse; las queries que lance
     * requieren adjuntar el Bearer token.
     */
    static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/graphiql", "/graphiql/**",
            "/v3/api-docs", "/v3/api-docs/**",
            "/swagger-ui.html", "/swagger-ui/**",
            // Rutas de fallback del circuit breaker (tarea 9.2, Requisito 9, criterio 4): deben
            // responder aunque la petición original fuese autenticada, por eso se permiten.
            "/fallback/**"
    };

    /**
     * Cadena de seguridad de producción: resource server reactivo con validación de JWT contra el
     * JWKS. Activa en todos los perfiles salvo {@code test} (ver {@link #testSecurityWebFilterChain}).
     */
    @Bean
    @Profile("!test")
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http, CorsConfigurationSource corsConfigurationSource) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // CORS con política acotada por entorno (tarea 13.5, Requisito 13, criterio 5). El
                // frontend corre en otro origen (Vercel/localhost:3000), por lo que el navegador
                // exige una respuesta CORS válida en el borde. Ver corsConfigurationSource(): NO se
                // usa comodín con credenciales; los orígenes permitidos vienen de ALLOWED_ORIGINS.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Cabeceras de seguridad en el borde (tarea 13.5, Requisito 13, criterio 5). Se
                // aplican a TODAS las respuestas del gateway, incluidas las de las rutas públicas.
                .headers(headers -> headers
                        // X-Content-Type-Options: nosniff — impide que el navegador "adivine" el
                        // MIME type (mitiga ataques por interpretación errónea de contenido).
                        .contentTypeOptions(contentType -> {})
                        // X-Frame-Options: DENY — el gateway no debe embeberse en iframes
                        // (defensa en profundidad contra clickjacking). El frontend en Vercel ya
                        // fija esta cabecera; aquí se cubre el borde de la API.
                        .frameOptions(frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        // Referrer-Policy: no filtrar la URL completa a orígenes cruzados.
                        .referrerPolicy(referrer -> {})
                        // HSTS: fuerza HTTPS en navegadores. Solo tiene efecto sobre conexiones
                        // seguras; en la demo el TLS lo termina el PaaS/Vercel/ALB (edge), no el
                        // propio gateway, por lo que la cabecera es coherente con ese despliegue.
                        .hsts(hsts -> hsts.includeSubdomains(true).maxAge(java.time.Duration.ofDays(365)))
                        // Content-Security-Policy conservadora. IMPORTANTE: se permite
                        // 'unsafe-inline' en scripts/estilos porque el gateway sirve las UIs
                        // GraphiQL y Swagger UI, que cargan scripts inline; una CSP más estricta
                        // (nonce/sha) las rompería. Contrapartida documentada en docs/security.
                        // No es un endpoint que renderice datos de usuario arbitrarios, así que el
                        // riesgo XSS es bajo. frame-ancestors 'none' refuerza X-Frame-Options.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "connect-src 'self'; "
                                        + "frame-ancestors 'none'")))
                .authorizeExchange(exchanges -> exchanges
                        // Las preflight CORS (OPTIONS) deben permitirse sin token: el navegador las
                        // envía sin credenciales antes de la petición real.
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        // El endpoint GraphQL exige JWT: `me` expone datos del usuario y su token se
                        // propaga a los servicios internos que agrega el BFF (tarea 9.3, criterio 5).
                        .pathMatchers("/graphql", "/graphql/**").authenticated()
                        // Las rutas de negocio se enrutan a downstream (ver application.yml) y exigen
                        // un JWT válido. Sin token: 401. El token se propaga tal cual a los servicios.
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Política CORS acotada por entorno (tarea 13.5, Requisito 13, criterio 5).
     *
     * <p>El frontend Next.js corre en un origen distinto del gateway (en local
     * {@code http://localhost:3000}; en producción, el dominio de Vercel), por lo que las llamadas
     * del navegador al borde de la API son <b>cross-origin</b> y requieren una respuesta CORS
     * válida. Los orígenes permitidos se leen de la variable {@code ALLOWED_ORIGINS} (lista separada
     * por comas), con un default de desarrollo ({@code http://localhost:3000}). <b>No</b> se
     * configura el comodín {@code *} junto con credenciales: eso está prohibido por la
     * especificación CORS y sería inseguro. El origen de producción se inyecta por entorno; no se
     * hardcodea aquí.
     *
     * <p>Se permite el header {@code Authorization} (el frontend envía el Bearer token del BFF) y
     * los métodos habituales. {@code allowCredentials(true)} habilita el envío de cookies/credenciales
     * cuando el origen está explícitamente listado.
     *
     * <p>Este bean también existe bajo el perfil {@code test} (no está anotado con {@code @Profile})
     * para que la cadena de seguridad de producción pueda ejercitarse en tests.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${antubank.gateway.cors.allowed-origins:http://localhost:3000}")
            String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        // allowedOrigins (exacto), NO allowedOriginPatterns con comodín: evita el comodín-con-
        // credenciales. Se filtran vacíos por si ALLOWED_ORIGINS trae comas sobrantes.
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Idempotency-Key", "Accept-Language", "Accept"));
        config.setExposedHeaders(List.of("Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(java.time.Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Cadena de seguridad para tests: permite todo el tráfico. Evita depender de Keycloak en los
     * tests de carga de contexto y de enrutamiento. La cadena de producción es la que ejercita la
     * validación real del JWT.
     */
    @Bean
    @Profile("test")
    SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
        return http.build();
    }

    /**
     * Convierte el JWT de Keycloak en authorities {@code ROLE_*} a partir de los roles de realm
     * ({@code realm_access.roles}), en su variante reactiva. Coherente con los resource servers de
     * los microservicios (tarea 8.2), lo que habilita autorización basada en roles en el borde.
     */
    static ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(new KeycloakRealmRoleConverter()));
        return converter;
    }

    /**
     * Extrae los roles de realm de Keycloak desde el claim {@code realm_access.roles} y los mapea a
     * {@link SimpleGrantedAuthority} con prefijo {@code ROLE_} (ej. {@code ROLE_CUSTOMER}).
     */
    static final class KeycloakRealmRoleConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        @SuppressWarnings("unchecked")
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null) {
                return List.of();
            }
            Object roles = realmAccess.get("roles");
            if (!(roles instanceof Collection<?> roleList)) {
                return List.of();
            }
            return roleList.stream()
                    .map(Object::toString)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(GrantedAuthority.class::cast)
                    .toList();
        }
    }
}
