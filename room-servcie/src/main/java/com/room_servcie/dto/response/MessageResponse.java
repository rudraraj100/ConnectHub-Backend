package com.room_servcie.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class MessageResponse {
    private String        messageId;
    private String        roomId;
    private String        senderId;
    private String        senderName;
    private String        senderAvatar;
    private String        content;
    private String        type;
    private String        fileUrl;
    private boolean       isPinned;
    private boolean       isDeleted;
    private String        replyToId;
    private LocalDateTime createdAt;
}
