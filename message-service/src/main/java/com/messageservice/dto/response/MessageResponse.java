package com.messageservice.dto.response;

import lombok.*;
import java.time.LocalDateTime;

/**
 * MessageResponse — returned by all /messages endpoints.
 * Case study §4.3 MessageResource response shape.
 */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class MessageResponse {
    private String        messageId;
    private String        roomId;
    private String        senderId;
    private String        senderName;    // enriched from auth-service
    private String        senderAvatar;  // enriched from auth-service
    private String        content;
    private String        type;          // TEXT | IMAGE | FILE | REACTION | SYSTEM
    private String        mediaUrl;
    private String        replyToMessageId;
    private boolean       isEdited;
    private boolean       isDeleted;
    private String        deliveryStatus; // SENT | DELIVERED | READ
    private LocalDateTime sentAt;
    private LocalDateTime editedAt;
}
