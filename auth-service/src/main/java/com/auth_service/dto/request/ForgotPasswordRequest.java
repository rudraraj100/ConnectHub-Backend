package com.auth_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;
}
