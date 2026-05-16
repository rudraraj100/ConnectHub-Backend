package com.notification_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket / STOMP configuration for the notification-service.
 *
 * NOTE (architecture): No frontend client currently connects to this endpoint.
 * The Angular frontend connects exclusively to websocket-handler (:8087/ws).
 * Real-time notification pushes for ONLINE users are sent directly by
 * websocket-handler via /topic/user/{userId} on its own broker.
 *
 * This config is retained for future use — e.g., a dedicated admin dashboard,
 * a mobile push adapter, or a server-sent-events upgrade could connect here.
 *
 * Endpoint (via gateway): ws://localhost:8080/ws-notifications
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // BUG 2 FIX: Removed .withSockJS().
        // The API Gateway route for /ws-notifications/** uses ws:// protocol,
        // which performs a direct WebSocket upgrade — it does NOT handle SockJS's
        // HTTP polling handshake (GET /ws-notifications/info).
        // Using .withSockJS() here caused the frontend to receive an HTTP 200
        // response body instead of a 101 Upgrade, silently failing the connection.
        // This matches the pattern used by websocket-handler's WebSocketConfig,
        // which already omits SockJS and works correctly.
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("*");
    }
}