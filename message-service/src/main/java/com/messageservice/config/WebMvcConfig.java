package com.messageservice.config;

/**
 * WebMvcConfig — intentionally empty.
 *
 * In this microservices architecture:
 *   - CORS is handled exclusively by the API Gateway (CorsFilter)
 *   - JWT validation is handled exclusively by the API Gateway (JwtGatewayFilter)
 *   - message-service trusts the X-User-Id / X-User-Email headers injected by the gateway
 *   - No security config, no CORS config, no auth logic belongs in this service
 */
public class WebMvcConfig {
    // No beans — CORS and security are gateway concerns only
}
