package com.api_gateway;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig — Gateway-level Swagger configuration.
 *
 * The API Gateway hosts a unified Swagger UI that aggregates the /v3/api-docs
 * from all downstream microservices. Users can switch between services via the
 * top-right dropdown on http://localhost:8080/swagger-ui.html
 *
 * Each service's docs URL is registered under springdoc.swagger-ui.urls in
 * application.yaml and proxied through the gateway routes (id: *-docs).
 *
 * The JWT Bearer security scheme is declared here so that the "Authorize" button
 * appears in the aggregated UI and the token is forwarded to every downstream call.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — API Gateway")
                        .version("1.0.0")
                        .description("""
                                Unified API documentation for the ConnectHub real-time chat platform.
                                
                                Use the **top-right dropdown** to switch between microservices:
                                - **Auth Service** — identity, JWT, OAuth2, profile, user management
                                - **Room Service** — chat rooms, members, admin controls
                                - **Message Service** — send/receive/pin/delete messages
                                - **Media Service** — upload & stream images and videos
                                - **Presence Service** — online/offline heartbeat and status
                                - **Notification Service** — in-app and email notifications
                                - **Payment Service** — Razorpay premium subscription
                                
                                Click **Authorize 🔒** and paste your JWT token (without the `Bearer ` prefix)
                                to authenticate all requests.
                                """)
                        .contact(new Contact()
                                .name("ConnectHub Team")
                                .email("support@connecthub.io")))
                // Declare the global JWT Bearer security scheme
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT access token here (without the 'Bearer ' prefix)")));
    }
}
