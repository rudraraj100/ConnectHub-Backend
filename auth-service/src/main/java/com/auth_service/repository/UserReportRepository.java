package com.auth_service.repository;

import com.auth_service.entity.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, String> {

    /** All reports, newest first — used by admin panel */
    List<UserReport> findAllByOrderByCreatedAtDesc();

    /** Reports by status — e.g. PENDING only */
    List<UserReport> findByStatusOrderByCreatedAtDesc(UserReport.ReportStatus status);

    /** How many pending reports a user has received (for risk scoring) */
    long countByReportedUserIdAndStatus(String reportedUserId, UserReport.ReportStatus status);
}
