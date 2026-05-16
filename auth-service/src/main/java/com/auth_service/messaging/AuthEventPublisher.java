package com.auth_service.messaging;

import com.auth_service.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Published after a user successfully verifies their email.
     * Triggers a "Welcome to ConnectHub!" in-app notification.
     */
    public void publishUserVerified(String userId, String email, String fullName) {
        publish(RabbitMQConfig.KEY_USER_VERIFIED,
                new AuthEventMessage(userId, email, fullName, "USER_VERIFIED"));
    }

    /**
     * Published after a user's plan is upgraded to PREMIUM.
     * Triggers a "You're now Premium!" in-app notification.
     */
    public void publishPlanUpgraded(String userId, String email, String fullName) {
        publish(RabbitMQConfig.KEY_PLAN_UPGRADED,
                new AuthEventMessage(userId, email, fullName, "PLAN_UPGRADED"));
    }

    // ── private helper ───────────────────────────────────────────
    private void publish(String routingKey, AuthEventMessage event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AUTH_EXCHANGE, routingKey, event);
            log.info("[RabbitMQ] Published {} for userId={}", routingKey, event.getUserId());
        } catch (Exception ex) {
            // Non-fatal: log and continue — do not propagate
            log.error("[RabbitMQ] Failed to publish {}: {}", routingKey, ex.getMessage());
        }
    }
}
