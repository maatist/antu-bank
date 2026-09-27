package cl.antubank.account.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Configuración de seguridad del account-service como <b>OAuth2 Resource Server</b>
 * (tarea 8.2, Requisito 8, criterios 4, 5 y 6).
 *
 * <p>El servicio valida el JWT emitido por Keycloak contra el <b>JWKS</b> del realm
 * {@code antu-bank} (clave pública publicada en
 * {@code .../protocol/openid-connect/certs}). La validación es local: tras descargar y
 * cachear el JWKS, la firma se verifica sin llamar a Keycloak en cada request. El
 * {@code issuer-uri}/{@code jwk-set-uri} se configura en {@code application.yml} y es
 * sobreescribible por entorno (variable {@code KEYCLOAK_ISSUER_URI}).
 *
 * <p>Autorización de endpoints:
 * <ul>
 *   <li>Se <b>permiten</b> los endpoints de infraestructura y documentación: health de actuator,
 *       {@code /v3/api-docs/**}, {@code /swagger-ui/**} y {@code /swagger-ui.html}.</li>
 *   <li>Los endpoints de negocio ({@code /accounts/**}) exigen un JWT válido: sin token la
 *       respuesta es {@code 401} (criterio 5) y sin el rol requerido es {@code 403} (criterio 6).</li>
 *   <li><b>Regla de rol</b> (tarea 8.5, criterio 6): la <b>apertura de cuentas</b>
 *       ({@code POST /accounts}) es una operación de cliente y exige el rol {@code CUSTOMER};
 *       un JWT válido sin ese rol recibe {@code 403}. La consulta de cuentas
 *       ({@code GET /accounts/**}) solo exige autenticación. La regla es mínima e intencional:
 *       demuestra la autorización basada en roles sin sobre-restringir el resto de la API.</li>
 * </ul>
 *
 * <p>Los roles de realm de Keycloak ({@code realm_access.roles}) se mapean a authorities de
 * Spring con prefijo {@code ROLE_} para habilitar la autorización basada en roles (tareas 8.3/8.5;
 * roles {@code CUSTOMER} y {@code ADMIN}).
 */
@Configuration
public class SecurityConfig {

    /** Rutas de infraestructura/documentación abiertas (sin autenticación). */
    static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**",
            "/swagger-ui.html", "/swagger-ui/**"
    };

    /**
     * Cadena de seguridad de producción: resource server con validación de JWT contra el JWKS.
     * Activa en todos los perfiles salvo {@code test} (ver {@link #testSecurityFilterChain}).
     */
    @Bean
    @Profile("!test")
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Regla de rol mínima (tarea 8.5, criterio 6): abrir una cuenta es una
                        // operación de cliente; exige el rol CUSTOMER. Un JWT válido sin el rol
                        // recibe 403.
                        .requestMatchers(HttpMethod.POST, "/accounts").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                // Auditoría de accesos (tarea 8.3, criterio 7): tras el filtro de autorización, con
                // el SecurityContext ya poblado, para auditar usuario, rol, endpoint y resultado.
                .addFilterAfter(new AccessAuditFilter(), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * Cadena de seguridad para tests: permite todo el tráfico. Mantiene la seguridad real en
     * producción y evita que los tests de integración existentes (que llaman sin token) reciban
     * {@code 401}. Los tests de autorización (tarea 8.5) ejercitan la cadena de producción.
     */
    @Bean
    @Profile("test")
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // La auditoría también opera bajo la cadena de test; sin autenticación real el
                // usuario queda como {@code anonymous} (el filtro maneja el caso con gracia).
                .addFilterAfter(new AccessAuditFilter(), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * Convierte el JWT de Keycloak en un {@link JwtAuthenticationConverter} que extrae los roles
     * de realm ({@code realm_access.roles}) y los expone como authorities {@code ROLE_*}.
     */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    /**
     * Extrae los roles de realm de Keycloak desde el claim {@code realm_access.roles} y los mapea
     * a {@link SimpleGrantedAuthority} con prefijo {@code ROLE_} (ej. {@code ROLE_CUSTOMER}).
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
