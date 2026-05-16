package com.auth_service.dto.response;

import com.auth_service.entity.UserReport;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserReportResponse {
    private String reportId;
    private String reporterId;
    private String reporterUsername;
    private String reportedUserId;
    private String reportedUsername;
    private String reason;
    private String details;
    private String status;          // PENDING | RESOLVED | DISMISSED
    private LocalDateTime createdAt;

    public static UserReportResponse from(UserReport r) {
        return UserReportResponse.builder()
                .reportId(r.getReportId())
                .reporterId(r.getReporterId())
                .reporterUsername(r.getReporterUsername())
                .reportedUserId(r.getReportedUserId())
                .reportedUsername(r.getReportedUsername())
                .reason(r.getReason())
                .details(r.getDetails())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
