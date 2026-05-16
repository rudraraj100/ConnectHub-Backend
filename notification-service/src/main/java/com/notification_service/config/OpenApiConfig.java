package com.notification_service.config;

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
 * OpenApiConfig — Notification Service Swagger / OpenAPI 3.0 configuration.
 *
 * Exposes a machine-readable spec at:  GET /api-docs
 * Exposes the interactive UI at:       GET /swagger-ui.html
 *
 * The API Gateway aggregates this spec at:
 *   http://localhost:8080/swagger-ui.html  →  dropdown: "Notification Service"
 *
 * This service has no Spring Security configuration — all endpoints are
 * publicly accessible from within the internal network. Authentication is
 * enforced at the API Gateway (JwtGatewayFilter) before requests reach here.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — Notification Service")
                        .version("1.0.0")
                        .description("""
                                Stores, dispatches and manages in-app notifications for ConnectHub.
                                
                                **Responsibilities:**
                                - Store per-user notification records (persisted in MySQL)
                                - Deliver real-time WebSocket push via STOMP `/topic/notifications/{userId}`
                                - Send email alerts for offline users (after configurable threshold)
                                - Mark individual / all notifications as read
                                - Delete notifications
                                - Bulk-send and platform-admin broadcast
                                
                                **Notification types:** `MESSAGE`, `MENTION`, `ROOM_INVITE`, `SYSTEM`
                                
                                **Inbound events (RabbitMQ):**
                                RabbitMQ consumers in `messaging/` package receive events from other
                                services (e.g., new message, room invite) and call `notifService.send()`.
                                
                                **Authentication note:**
                                No local Spring Security — the API Gateway enforces JWT auth before
                                routing to this service. User identity arrives via `X-User-Id` header.
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
                        .url("http://localhost:8086")
                        .description("Notification Service (direct — dev only)"))
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
