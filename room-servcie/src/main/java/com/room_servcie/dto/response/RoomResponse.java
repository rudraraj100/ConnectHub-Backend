package com.room_servcie.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class RoomResponse {
    private String        roomId;
    private String        name;
    private String        description;
    private String        type;         // GROUP | DM
    private String        avatarUrl;
    private int           maxMembers;
    private int           memberCount;
    private String        createdBy;
    private String        inviteLink;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private long          unreadCount;
    private String        currentUserRole; // ADMIN | MEMBER
}
