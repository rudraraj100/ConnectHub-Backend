package com.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long   notificationId;
    private String recipientId;
    private String actorId;
    private String type;
    private String title;
    private String message;
    private String roomId;
    private String messageId;

    // BUG 3 FIX: Lombok @Data generates isRead() for a primitive boolean field
    // named "isRead". Jackson uses the getter name and strips the "is" prefix,
    // serialising this field as "read" instead of "isRead".
    // @JsonProperty("isRead") forces the correct JSON key on both serialisation
    // (server → client) and deserialisation (client → server).
    @JsonProperty("isRead")
    private boolean isRead;

    private LocalDateTime createdAt;
}