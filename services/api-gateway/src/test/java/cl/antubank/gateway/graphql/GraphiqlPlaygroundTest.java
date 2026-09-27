package cl.antubank.gateway.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifica que el playground GraphiQL está habilitado y se sirve (tarea 9.3, Requisito 9,
 * criterio 6). Bajo el perfil {@code test} la cadena de seguridad permite todo y las rutas apuntan
 * a puertos muertos, por lo que este test solo comprueba que la UI de GraphiQL responde en
 * {@code /graphiql} (HTML), sin depender de servicios internos.
 */
@SpringBootTest
@ActiveProfiles("test")
class GraphiqlPlaygroundTest {

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void graphiqlSeSirveEnLaRutaEsperada() {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).build();

        // GraphiQL habilitado: /graphiql redirige (307) a /graphiql?path=/graphql (Spring for
        // GraphQL). El redirect confirma que el handler del playground está registrado; si estuviera
        // deshabilitado, la ruta daría 404. Se sigue el redirect y se verifica que sirve la UI (HTML).
        String location = client.get().uri("/graphiql")
                .exchange()
                .expectStatus().is3xxRedirection()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getLocation()
                .toString();

        org.assertj.core.api.Assertions.assertThat(location).contains("/graphiql");

        client.get().uri(location)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_HTML);
    }
}
