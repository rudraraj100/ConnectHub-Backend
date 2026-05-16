package com.presence_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig — Presence Service Swagger / OpenAPI 3.0 configuration.
 *
 * Exposes a machine-readable spec at:  GET /api-docs
 * Exposes the interactive UI at:       GET /swagger-ui.html
 *
 * The API Gateway aggregates this spec at:
 *   http://localhost:8080/swagger-ui.html  →  dropdown: "Presence Service"
 *
 * Presence data is stored in Redis with a TTL of ${app.presence.ttl-seconds} (35s).
 * The frontend pings /presence/heartbeat every 20 seconds to keep the user ONLINE.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI presenceServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — Presence Service")
                        .version("1.0.0")
                        .description("""
                                Tracks real-time user online/offline status for the ConnectHub platform.
                                
                                **Responsibilities:**
                                - Heartbeat endpoint: frontend pings every 20 s to stay `ONLINE`
                                - Explicit status update: `ONLINE | AWAY | OFFLINE`
                                - Single-user presence lookup
                                - Bulk presence lookup (for contact lists / room member displays)
                                
                                **How it works (Redis TTL pattern):**
                                1. `POST /presence/heartbeat` writes `presence:{userId}` to Redis with a
                                   35-second TTL
                                2. If no heartbeat arrives within 35 s, the key expires and the user is
                                   considered `OFFLINE`
                                3. Bulk reads check for key existence and return the stored status map
                                
                                **Authentication:** JWT resolved to `X-User-Id` by the API Gateway.
                                Bulk / read endpoints do not require the caller to own the queried userId.
                                """)
                        .contact(new Contact()
                                .name("ConnectHub Engineering")
                                .email("engineering@connecthub.io"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://connecthub.io/terms")))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("API Gateway (recommended for testing)"))
                .addServersItem(new Server()
                        .url("http://localhost:8085")
                        .description("Presence Service (direct — dev only)"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Obtain a JWT from POST /auth/login and paste the token here.")));
    }
}
