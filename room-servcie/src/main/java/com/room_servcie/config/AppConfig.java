package com.room_servcie.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * AppConfig — Room Service is a pure business-logic microservice.
 *
 * Security  → Auth Service is the sole authority.
 * CORS      → API Gateway is the sole authority (it is the browser-facing entry point).
 *             Room Service is NEVER called directly by the browser; all requests
 *             flow through the gateway. Adding CORS here would cause the
 *             Access-Control-Allow-Origin header to appear TWICE (gateway + room-service),
 *             which browsers reject with a CORS ERR_FAILED error.
 *
 * @LoadBalanced — allows lb://auth-service URIs when Eureka is active.
 */
@Configuration
public class AppConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
