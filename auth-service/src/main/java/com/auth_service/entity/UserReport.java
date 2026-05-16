package com.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists user-submitted abuse reports.
 * Reporter submits via POST /auth/reports; admin reviews via GET /auth/admin/reports.
 */
@Entity
@Table(name = "user_reports", indexes = {
        @Index(name = "idx_reports_status",      columnList = "status"),
        @Index(name = "idx_reports_reported_id", columnList = "reported_user_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserReport {

    @Id
    @Column(name = "report_id", updatable = false, nullable = false, length = 36)
    private String reportId;

    /** The user who filed the report */
    @Column(name = "reporter_id", nullable = false, length = 36)
    private String reporterId;

    @Column(name = "reporter_username", nullable = false, length = 50)
    private String reporterUsername;

    /** The user being reported */
    @Column(name = "reported_user_id", nullable = false, length = 36)
    private String reportedUserId;

    @Column(name = "reported_username", nullable = false, length = 50)
    private String reportedUsername;

    /** Reason category (e.g. "Harassment", "Spam") */
    @Column(nullable = false, length = 100)
    private String reason;

    /** Optional free-text details from the reporter */
    @Column(length = 1000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ReportStatus { PENDING, RESOLVED, DISMISSED }

    @PrePersist
    public void prePersist() {
        if (this.reportId == null) this.reportId = UUID.randomUUID().toString();
    }
}
