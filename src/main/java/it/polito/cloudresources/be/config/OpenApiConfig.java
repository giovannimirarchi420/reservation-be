package it.polito.cloudresources.be.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation configuration
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Cloud Resource Management API",
                version = "1.0.0",
                description = "API for managing cloud resources and bookings",
                contact = @Contact(
                        name = "Cloud Resources Team",
                        email = "support@cloudresources.com"
                )
        ),
        servers = {
                @Server(
                        url = "/api",
                        description = "Server URL"
                )
        }
)
@SecurityScheme(
        name = "bearer-auth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
    // Configuration class for OpenAPI documentation
}
