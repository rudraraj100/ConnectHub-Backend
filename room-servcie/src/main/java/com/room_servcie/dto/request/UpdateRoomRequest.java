package com.room_servcie.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRoomRequest {
    @Size(min = 2, max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    private String avatarUrl;

    @Min(2) @Max(500)
    private Integer maxMembers;
}
