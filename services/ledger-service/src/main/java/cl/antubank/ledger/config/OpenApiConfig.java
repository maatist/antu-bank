package cl.antubank.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata de la documentación OpenAPI del ledger-service (en español).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Antu Bank — ledger-service")
                        .description("API de contabilidad de doble entrada (asientos inmutables y saldos derivados).")
                        .version("v1")
                        .contact(new Contact().name("Antu Bank"))
                        .license(new License().name("MIT")));
    }
}
