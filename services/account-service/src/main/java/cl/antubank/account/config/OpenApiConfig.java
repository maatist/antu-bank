package cl.antubank.account.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata de la documentación OpenAPI del account-service (en español).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI accountServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Antu Bank — account-service")
                        .description("API de gestión de cuentas bancarias (RUT, tipo de cuenta y banco chileno).")
                        .version("v1")
                        .contact(new Contact().name("Antu Bank"))
                        .license(new License().name("MIT")));
    }
}
