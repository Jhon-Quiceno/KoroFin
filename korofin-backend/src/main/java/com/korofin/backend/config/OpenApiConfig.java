package com.korofin.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * KoroFin es mobile-only: no hay origen de frontend web que documentar como servidor adicional
 * (a diferencia de FinSmart, que exponía {@code app.cors.allowed-origins} acá). Swagger UI solo se
 * expone en el perfil {@code dev} (ver {@code application-dev.properties}) para pruebas manuales.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KoroFin API")
                        .version("1.0.0")
                        .description("API de gestión financiera personal")
                        .contact(new Contact()
                                .name("KoroFin")
                                .email("dev@korofin.jhonqui.dev")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Servidor local")
                ));
    }
}
