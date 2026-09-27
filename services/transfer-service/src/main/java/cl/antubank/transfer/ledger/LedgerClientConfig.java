package cl.antubank.transfer.ledger;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuración del cliente HTTP hacia ledger-service (tarea 4.3).
 *
 * <p>Expone un {@link RestClient.Builder} enlazado a la URL base configurable
 * ({@code ledger.base-url}) y el {@link LedgerClient} construido sobre él. El builder se expone
 * como bean para permitir su intercepción en tests (por ejemplo, con {@code MockRestServiceServer})
 * sin levantar un ledger-service real.
 */
@Configuration
@EnableConfigurationProperties(LedgerProperties.class)
public class LedgerClientConfig {

    /**
     * Builder de {@link RestClient} preconfigurado con la URL base del ledger-service.
     */
    @Bean
    public RestClient.Builder ledgerRestClientBuilder(LedgerProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl());
    }

    /**
     * Cliente del ledger-service usado por la lógica de transferencias.
     */
    @Bean
    public LedgerClient ledgerClient(RestClient.Builder ledgerRestClientBuilder) {
        return new LedgerClient(ledgerRestClientBuilder.build());
    }
}
