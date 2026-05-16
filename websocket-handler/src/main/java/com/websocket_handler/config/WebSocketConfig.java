package com.websocket_handler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocketConfig — STOMP message broker configuration.
 *
 * Topics:
 *   /topic/room/{roomId}   — all room messages and events
 *   /topic/user/{userId}   — personal alerts and DM notifications
 *   /topic/presence        — online/offline presence broadcasts
 *
 * Inbound endpoints:
 *   /app/chat.send         — send a chat message
 *   /app/chat.typing       — typing indicator
 *   /app/chat.read         — read receipt
 *   /app/chat.react        — message reaction
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker for /topic and /queue
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(heartbeatScheduler())
                .setHeartbeatValue(new long[]{10000, 10000});  // 10s heartbeat

        // Prefix for messages handled by @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        // No .withSockJS() — frontend uses native WebSocket (ws:// via reactive gateway)
    }

    /** TaskScheduler required by STOMP heartbeat */
    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
