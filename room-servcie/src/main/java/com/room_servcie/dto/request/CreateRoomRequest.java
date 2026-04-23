package com.room_servcie.dto.request;

import com.room_servcie.entity.RoomType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateRoomRequest {
    @NotBlank(message = "Room name is required")
    @Size(min = 2, max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull
    private RoomType type = RoomType.GROUP;

    @Min(2) @Max(500)
    private int maxMembers = 100;

    private String avatarUrl;

    /** For DM — the other user's ID */
    private String targetUserId;
}
