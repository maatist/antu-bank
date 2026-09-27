package cl.antubank.transfer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata de la documentación OpenAPI del transfer-service (en español).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transferServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Antu Bank — transfer-service")
                        .description("API de transferencias idempotentes en CLP (persiste Idempotency-Key con su resultado).")
                        .version("v1")
                        .contact(new Contact().name("Antu Bank"))
                        .license(new License().name("MIT")));
    }
}
