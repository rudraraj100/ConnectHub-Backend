package com.auth_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$",
        message = "Please enter a valid email address (e.g. user@example.com)"
    )
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+=\\[\\]{};:,.<>?\\-])[A-Za-z\\d@$!%*?&#^()_+=\\[\\]{};:,.<>?\\-]{8,}$",
        message = "Password must include uppercase, lowercase, number and special character (@$!%*?&#)"
    )
    private String password;

    @Size(max = 100)
    private String country;

    @Size(max = 100)
    private String city;

    /** Dial prefix e.g. "+91" */
    @Size(max = 10)
    private String countryCode;

    @Pattern(
        regexp = "^$|^[0-9]{5,15}$",
        message = "Phone number must be 5–15 digits"
    )
    private String phoneNumber;
}
