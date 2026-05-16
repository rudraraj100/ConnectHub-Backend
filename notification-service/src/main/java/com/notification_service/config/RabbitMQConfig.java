package com.notification_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology — mirrors the config declared in auth-service.
 *
 * Exchange : ch.auth.events  (topic, durable)
 * Queue    : notification.auth.queue (durable)
 * Binding  : auth.# → notification.auth.queue
 *
 * Spring AMQP will create these if they don't already exist.
 */
@Configuration
public class RabbitMQConfig {

    public static final String AUTH_EXCHANGE      = "ch.auth.events";
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
        return BindingBuilder.bind(notificationQueue)
                .to(authExchange)
                .with("auth.#");
    }

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
