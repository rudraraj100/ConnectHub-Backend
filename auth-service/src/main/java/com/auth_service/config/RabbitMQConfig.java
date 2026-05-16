package com.auth_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for auth events.
 *
 * Exchange : ch.auth.events  (topic, durable)
 * Queue    : notification.auth.queue (durable)
 * Bindings : auth.#  →  notification.auth.queue
 *
 * Routing keys published by this service:
 *   auth.user.verified   — after a user clicks the email-verification link
 *   auth.plan.upgraded   — after a user's plan is promoted to PREMIUM
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchange ──────────────────────────────────────────────────
    public static final String AUTH_EXCHANGE   = "ch.auth.events";

    // ── Routing keys ──────────────────────────────────────────────
    public static final String KEY_USER_VERIFIED  = "auth.user.verified";
    public static final String KEY_PLAN_UPGRADED  = "auth.plan.upgraded";

    // ── Queue ─────────────────────────────────────────────────────
    public static final String NOTIFICATION_QUEUE = "notification.auth.queue";

    @Bean
    TopicExchange authExchange() {
        return new TopicExchange(AUTH_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange authExchange) {
        // 'auth.#' catches both auth.user.verified and auth.plan.upgraded
        return BindingBuilder.bind(notificationQueue)
                .to(authExchange)
                .with("auth.#");
    }

    // ── Jackson converter: publish/consume as JSON ────────────────
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(jsonMessageConverter());
        return tpl;
    }
}
