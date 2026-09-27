package cl.antubank.gateway.graphql;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClients reactivos que usa el BFF GraphQL para llamar a los servicios internos
 * (tarea 9.3, Requisito 9, criterio 5).
 *
 * <p>Se crea un {@link WebClient} por servicio con su base URL configurable
 * ({@link BffProperties}). La <b>propagación del token</b> no se fija aquí de forma global: cada
 * llamada adjunta el {@code Authorization: Bearer <JWT>} entrante mediante un header por-request
 * (ver {@code DownstreamBffClient}), de modo que el token del usuario autenticado viaja a
 * account/ledger/transfer, que lo validan como resource servers (tarea 8.2).
 */
@Configuration
@EnableConfigurationProperties(BffProperties.class)
public class BffWebClientConfig {

    @Bean
    WebClient accountServiceWebClient(WebClient.Builder builder, BffProperties props) {
        return builder.baseUrl(props.accountServiceUri()).build();
    }

    @Bean
    WebClient ledgerServiceWebClient(WebClient.Builder builder, BffProperties props) {
        return builder.baseUrl(props.ledgerServiceUri()).build();
    }

    @Bean
    WebClient transferServiceWebClient(WebClient.Builder builder, BffProperties props) {
        return builder.baseUrl(props.transferServiceUri()).build();
    }
}
