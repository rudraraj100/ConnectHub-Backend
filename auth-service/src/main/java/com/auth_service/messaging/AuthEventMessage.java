package com.auth_service.messaging;

import lombok.*;

/**
 * Payload published to RabbitMQ for auth domain events.
 * Must be serialisable to/from JSON by Jackson.
 *
 * eventType values: USER_VERIFIED | PLAN_UPGRADED
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthEventMessage {
    private String userId;
    private String email;
    private String fullName;
    private String eventType;
}
