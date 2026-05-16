package com.notification_service.messaging;

import lombok.*;

/**
 * Inbound payload from RabbitMQ — published by auth-service.
 *
 * eventType: USER_VERIFIED | PLAN_UPGRADED
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
