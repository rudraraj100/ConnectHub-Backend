package com.auth_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;

    @Size(max = 512, message = "Avatar URL too long")
    private String avatarUrl;
}
