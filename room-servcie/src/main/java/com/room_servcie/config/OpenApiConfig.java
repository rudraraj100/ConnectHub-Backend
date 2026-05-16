package com.room_servcie.config;

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
 * OpenApiConfig — Room Service Swagger / OpenAPI 3.0 configuration.
 *
 * Exposes a machine-readable spec at:  GET /api-docs
 * Exposes the interactive UI at:       GET /swagger-ui.html
 *
 * The API Gateway aggregates this spec at:
 *   http://localhost:8080/swagger-ui.html  →  dropdown: "Room Service"
 *
 * JWT Bearer security scheme is declared here so every endpoint shows
 * the "Authorize 🔒" padlock and passes the token in the Authorization header.
 * The gateway injects X-User-Id / X-User-Plan after validating the JWT,
 * so downstream controllers never parse tokens directly.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI roomServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — Room Service")
                        .version("1.0.0")
                        .description("""
                                Manages chat rooms (DMs and group channels) for the ConnectHub platform.
                                
                                **Responsibilities:**
                                - Create / update / delete rooms (DMs are free; group rooms require PREMIUM)
                                - Membership management: add / remove / mute members, change roles
                                - Join via invite link
                                - In-room message relay (delegates to message-service)
                                - Pin / unpin messages (PREMIUM, room-admin only)
                                - Mark room as read; paginated message history
                                - Platform-admin: list all rooms, hard-delete any room
                                
                                **Plan gates:**
                                | Feature | FREE | PREMIUM |
                                |---|---|---|
                                | DM rooms | ✅ | ✅ |
                                | Group rooms | ❌ | ✅ |
                                | Pin messages | ❌ | ✅ |
                                
                                **Authentication:** JWT injected as `X-User-Id` header by the API Gateway.
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
                        .url("http://localhost:8082")
                        .description("Room Service (direct — dev only)"))
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
