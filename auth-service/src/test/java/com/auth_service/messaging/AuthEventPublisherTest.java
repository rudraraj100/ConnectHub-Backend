package com.auth_service.messaging;

import com.auth_service.config.RabbitMQConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthEventPublisher - unit tests")
class AuthEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AuthEventPublisher sut;

    @Test
    @DisplayName("publishUserVerified() sends correct message to exchange")
    void publishUserVerified_sendsMessage() {
        sut.publishUserVerified("u1", "e1", "n1");

        // Use explicit 3-arg overload to avoid ambiguity
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.AUTH_EXCHANGE),
                eq(RabbitMQConfig.KEY_USER_VERIFIED),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("publishPlanUpgraded() sends correct message")
    void publishPlanUpgraded_sendsMessage() {
        sut.publishPlanUpgraded("u1", "e1", "n1");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.AUTH_EXCHANGE),
                eq(RabbitMQConfig.KEY_PLAN_UPGRADED),
                any(Object.class)
        );
    }

    @Test
    @DisplayName("publish() handles exceptions gracefully")
    void publish_handlesException() {
        doThrow(new RuntimeException("Rabbit down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        // Should not throw exception
        sut.publishUserVerified("u1", "e1", "n1");

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
