package com.room_servcie.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class RoomMemberResponse {
    private String        userId;
    private String        username;
    private String        fullName;
    private String        avatarUrl;
    private String        role;        // ADMIN | MEMBER
    private boolean       isMuted;
    private String        status;      // ONLINE | OFFLINE | AWAY (from auth-service)
    private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
}
