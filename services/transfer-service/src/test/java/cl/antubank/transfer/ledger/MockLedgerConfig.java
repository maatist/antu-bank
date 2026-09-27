package cl.antubank.transfer.ledger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Configuración de test que sustituye el ledger-service real por un {@link MockRestServiceServer}.
 *
 * <p>Permite validar la interacción del transfer-service con el ledger (validación de fondos y
 * registro de la doble entrada) sin levantar un segundo servicio. Se define un {@link LedgerClient}
 * primario construido sobre un {@link RestClient} enlazado al servidor simulado; así se interceptan
 * todas las llamadas HTTP salientes sin colisionar con los beans de producción.
 */
@TestConfiguration
public class MockLedgerConfig {

    private MockRestServiceServer server;

    /**
     * Cliente del ledger primario, enlazado al servidor simulado. Sobrescribe al de producción
     * para que la lógica de transferencias use el transporte interceptado.
     */
    @Bean
    @Primary
    public LedgerClient mockLedgerClient() {
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        return new LedgerClient(builder.build());
    }

    /**
     * Servidor HTTP simulado del ledger. Los tests fijan expectativas ({@code expect(...)}) y
     * verifican las llamadas realizadas ({@code verify()}).
     */
    @Bean
    public MockRestServiceServer ledgerMockServer(LedgerClient mockLedgerClient) {
        // El parámetro fuerza a que el cliente (y su enlace al servidor) exista primero.
        return server;
    }
}
