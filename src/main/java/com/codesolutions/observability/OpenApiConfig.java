package com.codesolutions.observability.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI configuration.
 *
 * Swagger UI:   /swagger-ui.html
 * OpenAPI JSON: /v3/api-docs
 * OpenAPI YAML: /v3/api-docs.yaml
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Observability Spring Boot API")
                        .version("0.1.0")
                        .description("""
                                Production-ready Spring Boot 3 starter with full observability stack.

                                Demonstrates: OpenTelemetry tracing, Micrometer/Prometheus metrics,
                                structured JSON logging, /health and /isready probes, OpenAPI/Swagger,
                                Spring Security base, global exception handler, and request correlation ID.
                                """)
                        .contact(new Contact()
                                .name("Ivã Martins")
                                .url("https://ivamartins.github.io/code-solutions-site/")
                                .email("ivamartins@gmail.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local dev"),
                        new Server().url("https://api.example.com").description("Production")
                ));
    }
}
