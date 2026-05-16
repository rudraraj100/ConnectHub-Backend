package com.auth_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig — Auth Service Swagger / OpenAPI 3.0 configuration.
 *
 * Exposes a machine-readable spec at:  GET /api-docs
 * Exposes the interactive UI at:       GET /swagger-ui.html
 *
 * The API Gateway aggregates this spec at:
 *   http://localhost:8080/swagger-ui.html  →  dropdown: "Auth Service"
 *
 * JWT Bearer security scheme is declared here so every endpoint shows
 * the "Authorize 🔒" padlock and passes the token in the Authorization header.
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public OpenAPI authServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — Auth Service")
                        .version("1.0.0")
                        .description("""
                                Manages the complete user identity lifecycle for the ConnectHub platform.
                                
                                **Responsibilities:**
                                - User registration with email OTP verification
                                - JWT-based login / logout / token refresh
                                - Google OAuth2 social login
                                - Profile management (name, avatar, bio, status)
                                - Password change, forgot-password, reset-password flows
                                - Platform-admin: list/suspend/delete users, manage reports
                                - Internal plan upgrade endpoint (called by payment-service)
                                
                                **Authentication:** All protected endpoints require a valid JWT in the
                                `Authorization: Bearer <token>` header. Public endpoints (register, login,
                                verify-otp, etc.) are listed in SecurityConfig and require no token.
                                """)
                        .contact(new Contact()
                                .name("ConnectHub Engineering")
                                .email("engineering@connecthub.io")
                                .url("https://github.com/connecthub"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://connecthub.io/terms")))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("API Gateway (use this for testing)"))
                .addServersItem(new Server()
                        .url("http://localhost:8081")
                        .description("Auth Service (direct — dev only)"))
                // Apply JWT security globally to all operations
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("""
                                                Obtain a JWT from `POST /auth/login`, then paste the token
                                                value here (without the `Bearer ` prefix).
                                                Token lifetime: 24 h  |  Refresh token lifetime: 7 days
                                                """)));
    }
}
