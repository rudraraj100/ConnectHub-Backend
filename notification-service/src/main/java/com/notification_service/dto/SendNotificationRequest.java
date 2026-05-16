package com.notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendNotificationRequest {

    @NotBlank
    private String recipientId;

    private String actorId;

    @NotBlank
    private String type;    // NEW_MESSAGE | MENTION | ROOM_INVITE | SYSTEM

    @NotBlank
    private String title;

    private String message;
    private String roomId;
    private String messageId;
}
