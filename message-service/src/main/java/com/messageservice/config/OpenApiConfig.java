package com.messageservice.config;

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
 * OpenApiConfig — Message Service Swagger / OpenAPI 3.0 configuration.
 *
 * Exposes a machine-readable spec at:  GET /api-docs
 * Exposes the interactive UI at:       GET /swagger-ui.html
 *
 * The API Gateway aggregates this spec at:
 *   http://localhost:8080/swagger-ui.html  →  dropdown: "Message Service"
 *
 * This service is primarily called internally by room-service and the
 * WebSocket handler. Public callers should prefer the room-service API.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI messageServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — Message Service")
                        .version("1.0.0")
                        .description("""
                                Provides durable storage and retrieval of chat messages for ConnectHub.
                                
                                **Responsibilities:**
                                - Persist and retrieve messages (by room, by ID, before-timestamp)
                                - Soft-delete and edit messages (sender only)
                                - Full-text keyword search within a room
                                - Delivery status management: `SENT → DELIVERED → READ`
                                - Bulk mark-read for a room (called by WebSocket handler on READ_RECEIPT)
                                - Pin / unpin a single message per room (PREMIUM feature)
                                
                                **Calling conventions:**
                                This service is mostly called internally by `room-service` and the
                                `websocket-handler`. Direct calls from the frontend go through the
                                API Gateway at `/messages/**`.
                                
                                **History limits (plan-gated):**
                                | Plan | Message history |
                                |---|---|
                                | FREE | Last 100 messages |
                                | PREMIUM | Unlimited |
                                
                                **Authentication:** JWT resolved to `X-User-Id` by the API Gateway.
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
                        .url("http://localhost:8083")
                        .description("Message Service (direct — dev only)"))
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
