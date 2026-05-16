package com.payment_service.config;

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
 * OpenApiConfig — Payment Service Swagger / OpenAPI 3.0 configuration.
 *
 * Exposes a machine-readable spec at:  GET /api-docs
 * Exposes the interactive UI at:       GET /swagger-ui.html
 *
 * The API Gateway aggregates this spec at:
 *   http://localhost:8080/swagger-ui.html  →  dropdown: "Payment Service"
 *
 * All payment endpoints require an authenticated user (JWT resolved to
 * X-User-Id by the API Gateway). Payment verification calls auth-service
 * DIRECTLY on port 8081 (not via gateway) to upgrade the user's plan.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectHub — Payment Service")
                        .version("1.0.0")
                        .description("""
                                Manages Razorpay-based premium subscription payments for ConnectHub.
                                
                                **Checkout flow:**
                                1. Frontend calls `POST /payments/orders` → receives a Razorpay `order_id`
                                2. Frontend opens the Razorpay payment modal (client-side SDK)
                                3. On success, Razorpay returns `razorpay_payment_id`, `razorpay_order_id`,
                                   `razorpay_signature`
                                4. Frontend calls `POST /payments/verify` with those three fields
                                5. Service verifies the HMAC signature; on success calls
                                   `PUT /auth/internal/upgrade-plan/{userId}` directly on auth-service
                                   to mint a new JWT with `plan=PREMIUM`
                                6. A fresh `AuthResponse` (new access + refresh token) is returned so
                                   the frontend can swap tokens without a re-login
                                
                                **Premium subscription:**
                                - Price: ₹499 / month (49 900 paise)
                                - Currency: INR
                                - Grants: unlimited group rooms, unlimited message history, message pinning
                                
                                **Authentication:** All endpoints require a valid JWT passed as
                                `Authorization: Bearer <token>` (enforced by the API Gateway).
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
                        .url("http://localhost:8088")
                        .description("Payment Service (direct — dev only)"))
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
