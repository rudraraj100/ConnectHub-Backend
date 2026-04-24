package com.api_gateway;

import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GatewayConfig — Programmatic route definitions for Spring Cloud Gateway Server MVC.
 *
 * YAML-based routes (spring.cloud.gateway.server.webmvc.routes) can sometimes be
 * silently ignored if the property key changes between Spring Cloud versions.
 * Programmatic RouterFunction beans are ALWAYS picked up by RouterFunctionMapping
 * and guarantee reliable routing.
 *
 * Architecture:
 *   - CORS is handled by CorsFilter (this gateway, runs before routing)
 *   - JWT validated by JwtGatewayFilter (this gateway, injects X-User-Id)
 *   - Backend services (message-service etc.) trust X-User-Id from gateway
 *   - No security config in any backend service
 */
@Configuration
public class GatewayConfig {

    /**
     * Routes all /messages/** requests to message-service on port 8083.
     *
     * The JwtGatewayFilter has already run before this handler executes,
     * so X-User-Id and X-User-Email are injected into the forwarded request.
     */
    @Bean
    public RouterFunction<ServerResponse> messageServiceRoute() {
        return RouterFunctions.route(
                RequestPredicates.path("/messages/**"),
                HandlerFunctions.http("http://localhost:8083")
        );
    }
}
