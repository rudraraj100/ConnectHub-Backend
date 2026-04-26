package com.notification_service.dto;

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
    private boolean isRead;
    private LocalDateTime createdAt;
}
