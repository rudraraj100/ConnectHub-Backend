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
    private String        senderName;     // enriched: fullName → username → senderId (never null)
    private String        senderUsername; // raw @handle from auth-service (may be null)
    private String        senderAvatar;  // enriched from auth-service
    private String        content;
    private String        type;          // TEXT | IMAGE | FILE | REACTION | SYSTEM
    private String        mediaType;     // IMAGE | VIDEO | FILE — Angular uses this to render media bubbles
    private String        mediaUrl;
    private String        replyToMessageId;
    private boolean       isEdited;
    private boolean       isDeleted;
    private boolean       isPinned;        // PREMIUM: true when room admin pinned this message
    private String        deliveryStatus; // SENT | DELIVERED | READ
    private LocalDateTime sentAt;
    private LocalDateTime editedAt;
}
