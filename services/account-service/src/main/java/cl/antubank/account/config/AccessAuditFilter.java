package cl.antubank.account.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de <b>auditoría de accesos</b> del account-service (tarea 8.3, Requisito 8, criterio 7;
 * diseño sección 7: "log de accesos con usuario, rol, endpoint y resultado").
 *
 * <p>Registra una entrada de auditoría estructurada por cada request a los endpoints del servicio,
 * una vez resuelta la cadena de seguridad. Los campos registrados son:
 * <ul>
 *   <li>{@code usuario}: subject/{@code preferred_username} del JWT autenticado, o {@code anonymous}
 *       cuando no hay principal autenticado (p. ej. bajo la cadena de test {@code permitAll} o en
 *       accesos rechazados con 401).</li>
 *   <li>{@code roles}: authorities del principal (roles {@code ROLE_*} mapeados desde Keycloak), o
 *       {@code none} si no aplica.</li>
 *   <li>{@code metodo}: método HTTP (GET, POST, ...).</li>
 *   <li>{@code ruta}: path del endpoint solicitado.</li>
 *   <li>{@code status}: código HTTP de la respuesta. Incluye accesos autorizados (2xx), no
 *       autenticados ({@code 401}) y prohibidos ({@code 403}), de modo que los accesos denegados
 *       también quedan auditados.</li>
 * </ul>
 *
 * <p>El log se emite bajo el logger dedicado {@code cl.antubank.account.security.AccessAudit} para
 * poder filtrarlo/enrutarlo de forma independiente. <b>Nunca</b> se registra el JWT en crudo ni
 * secretos: solo el identificador de usuario y sus roles.
 *
 * <p>Se registra dentro de la cadena de seguridad (después del filtro de autorización) para poder
 * leer el {@link org.springframework.security.core.context.SecurityContext SecurityContext} ya
 * poblado, y emite la auditoría al finalizar el request (tras resolver el status). El filtro nunca
 * interrumpe la petición: si la auditoría fallara, se registra el problema y el request continúa.
 */
public class AccessAuditFilter extends OncePerRequestFilter {

    /** Logger dedicado de auditoría, filtrable de forma independiente. */
    static final Logger AUDIT = LoggerFactory.getLogger("cl.antubank.account.security.AccessAudit");

    private static final String ANONYMOUS = "anonymous";
    private static final String NO_ROLES = "none";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            audit(request, response);
        }
    }

    /** Emite una entrada de auditoría estructurada (key=value) para el request completado. */
    private void audit(HttpServletRequest request, HttpServletResponse response) {
        try {
            AUDIT.info(
                    "acceso usuario={} roles={} metodo={} ruta={} status={}",
                    resolveUser(),
                    resolveRoles(),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus());
        } catch (RuntimeException ex) {
            // La auditoría nunca debe romper la petición; se deja traza del fallo y se continúa.
            AUDIT.warn("no se pudo registrar la auditoria de acceso: {}", ex.toString());
        }
    }

    /** Resuelve el identificador de usuario autenticado, o {@code anonymous} si no aplica. */
    private static String resolveUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
            return ANONYMOUS;
        }
        String name = auth.getName();
        return (name == null || name.isBlank()) ? ANONYMOUS : name;
    }

    /** Resuelve las authorities (roles) del principal como lista separada por comas. */
    private static String resolveRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return NO_ROLES;
        }
        String roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return roles.isBlank() ? NO_ROLES : roles;
    }

    /** Detecta el token anónimo de Spring Security (usado cuando no hay autenticación real). */
    private static boolean isAnonymous(Authentication auth) {
        return "anonymousUser".equals(auth.getPrincipal());
    }
}
