package cl.antubank.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Test unitario del {@link AccessAuditFilter} (tarea 8.3, Requisito 8, criterio 7).
 *
 * <p>Verifica que el filtro registra una entrada de auditoría con los campos esperados
 * (usuario, roles, método, ruta, status) para un usuario autenticado, que audita los accesos
 * denegados ({@code 403}), que trata como {@code anonymous} el caso sin autenticación, y que
 * nunca interrumpe la petición. Captura los logs del logger dedicado mediante un
 * {@link ListAppender} de Logback.
 */
class AccessAuditFilterTest {

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger =
                (ch.qos.logback.classic.Logger)
                        LoggerFactory.getLogger("cl.antubank.account.security.AccessAudit");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        auditLogger.setLevel(Level.INFO);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditaAccesoAutenticadoConUsuarioRolMetodoRutaYStatus() throws Exception {
        var auth =
                new UsernamePasswordAuthenticationToken(
                        "juan.perez",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        var request = new MockHttpServletRequest("GET", "/accounts/42");
        var response = new MockHttpServletResponse();
        response.setStatus(200);

        new AccessAuditFilter().doFilter(request, response, new MockFilterChain());

        String logged = singleAuditMessage();
        assertThat(logged).contains("usuario=juan.perez");
        assertThat(logged).contains("roles=ROLE_CUSTOMER");
        assertThat(logged).contains("metodo=GET");
        assertThat(logged).contains("ruta=/accounts/42");
        assertThat(logged).contains("status=200");
    }

    @Test
    void auditaAccesoDenegado403() throws Exception {
        var auth =
                new UsernamePasswordAuthenticationToken(
                        "sin.rol", "n/a", List.of(new SimpleGrantedAuthority("ROLE_OTHER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        var request = new MockHttpServletRequest("POST", "/accounts");
        var response = new MockHttpServletResponse();
        response.setStatus(403);

        new AccessAuditFilter().doFilter(request, response, new MockFilterChain());

        String logged = singleAuditMessage();
        assertThat(logged).contains("usuario=sin.rol");
        assertThat(logged).contains("metodo=POST");
        assertThat(logged).contains("ruta=/accounts");
        assertThat(logged).contains("status=403");
    }

    @Test
    void auditaAccesoAnonimoSinContextoDeSeguridad() throws Exception {
        // Sin autenticación (p. ej. bajo la cadena de test permitAll o un 401).
        var request = new MockHttpServletRequest("GET", "/accounts");
        var response = new MockHttpServletResponse();
        response.setStatus(401);

        assertThatCode(
                        () ->
                                new AccessAuditFilter()
                                        .doFilter(request, response, new MockFilterChain()))
                .doesNotThrowAnyException();

        String logged = singleAuditMessage();
        assertThat(logged).contains("usuario=anonymous");
        assertThat(logged).contains("roles=none");
        assertThat(logged).contains("status=401");
    }

    /** Devuelve el mensaje formateado del único evento de auditoría capturado. */
    private String singleAuditMessage() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0).getFormattedMessage();
    }
}
