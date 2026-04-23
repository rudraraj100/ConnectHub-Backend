package com.auth_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * UpdateProfileRequest — fields the user can edit from the Settings panel.
 * All fields are optional; only non-null values are applied (PATCH semantics).
 */
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

    // ── Location & contact (added from Settings panel) ─────────────
    @Size(max = 100)
    private String country;

    @Size(max = 100)
    private String city;

    /** Dial prefix e.g. "+91" */
    @Size(max = 10)
    private String countryCode;

    /** Phone number digits only */
    @Size(max = 20)
    private String phoneNumber;
}
