package com.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitReportRequest {

    /** Username of the user being reported — used to resolve their userId */
    @NotBlank(message = "Reported username is required")
    private String reportedUsername;

    /** Reason category selected from the dropdown */
    @NotBlank(message = "Reason is required")
    @Size(max = 100)
    private String reason;

    /** Optional free-text details (max 1000 chars) */
    @Size(max = 1000)
    private String details;
}
