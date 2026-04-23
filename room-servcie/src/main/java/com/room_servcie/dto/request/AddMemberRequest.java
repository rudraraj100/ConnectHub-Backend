package com.room_servcie.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddMemberRequest {
    @NotBlank(message = "userId is required")
    private String userId;

    @Size(max = 10)
    private String role = "MEMBER"; // ADMIN | MEMBER
}
