package com.auth_service.service;

import com.auth_service.dto.request.*;
import com.auth_service.dto.response.*;
import com.auth_service.entity.UserStatus;

import java.util.List;

/**
 * AuthService interface — §4.1 case study business contract.
 * Declares register, login, logout, validateToken, refreshToken,
 * getUserById, updateProfile, changePassword, searchUsers, updateStatus.
 * + Frontend additions: forgotPassword, resetPassword.
 */
public interface AuthService {

    /** Returns HTTP 202 body — does NOT return a JWT. User must verify email first. */
    String register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void logout(String token);

    AuthResponse refreshToken(String refreshToken);

    /** Validates the 6-digit OTP sent to the user's email and activates the account. */
    void verifyEmail(String email, String otp);

    /** Re-sends a verification email. Silently ignores unknown emails. */
    void resendVerification(String email);

    UserProfileResponse getUserById(String userId);

    UserProfileResponse updateProfile(String userId, UpdateProfileRequest request);

    void changePassword(String userId, ChangePasswordRequest request);

    List<UserProfileResponse> searchUsers(String keyword);

    UserProfileResponse updateStatus(String userId, UpdateStatusRequest request);

    boolean checkUsernameAvailability(String username);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    // ── Platform Admin ─────────────────────────────────────────
    List<UserProfileResponse> getAllUsers(String requesterId);

    UserProfileResponse toggleUserActive(String requesterId, String targetId);

    void adminDeleteUser(String requesterId, String targetId);

    // ── Internal: called by payment-service after verified payment ──
    /**
     * Upgrades a user's plan (e.g. FREE → PREMIUM) and issues a fresh JWT.
     * Returns a new AuthResponse so the frontend can swap its stored token
     * immediately — no re-login required after payment.
     *
     * @param userId the user to upgrade
     * @param plan   target plan string (e.g. "PREMIUM")
     */
    AuthResponse upgradePlan(String userId, String plan);

    // ── Reports ────────────────────────────────────────────────
    UserReportResponse submitReport(String reporterId, SubmitReportRequest request);

    List<UserReportResponse> getAllReports(String requesterId);

    UserReportResponse updateReportStatus(String requesterId, String reportId, String action);
}
