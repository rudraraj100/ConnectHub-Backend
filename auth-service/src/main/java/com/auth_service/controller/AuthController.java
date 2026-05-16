package com.auth_service.controller;

import com.auth_service.dto.request.*;
import com.auth_service.dto.response.*;
import com.auth_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AuthController manages the user lifecycle: registration, login, and security settings.
 * It is the entry point for identity management in ConnectHub.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User identity, JWT, OAuth2, profile and status")
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new user. 
     * The user starts as 'inactive' until they verify their email using the OTP sent to them.
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user — returns 202, sends verification email")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Registration accepted; verification email sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed (invalid email, weak password, etc.)", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or username already taken", content = @Content)
    })
    public ResponseEntity<ApiResponse<String>> register(
            @Valid @RequestBody RegisterRequest request) {

        String message = authService.register(request);
        // 202 Accepted — user must verify email before they can log in
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(message, null));
    }

    /**
     * Verifies the user's email using the 6-digit OTP code sent during registration.
     */
    @PostMapping("/verify-otp")
    @Operation(summary = "Verify the 6-digit OTP sent to the user's email")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified; user can now log in"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP", content = @Content)
    })
    public ResponseEntity<ApiResponse<String>> verifyOtp(
            @RequestParam("email") String email,
            @RequestParam("otp")   String otp) {

        authService.verifyEmail(email, otp);
        return ResponseEntity.ok(
                ApiResponse.ok("Email verified successfully! You can now log in.", null));
    }

    // ── RESEND VERIFICATION OTP ───────────────────────────────────

    @PostMapping("/resend-verification")
    @Operation(summary = "Re-send a 6-digit verification OTP (use when the original expired)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP resent (or no-op if already verified)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Email not registered", content = @Content)
    })
    public ResponseEntity<ApiResponse<String>> resendVerification(
            @RequestParam("email") String email) {

        authService.resendVerification(email);
        return ResponseEntity.ok(
                ApiResponse.ok("If that email is registered and unverified, a new OTP has been sent.", null));
    }

    // ── LOGIN ─────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login with email and password — returns JWT access + refresh tokens")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful; JWT tokens returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials or unverified email", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", auth));
    }

    // ── LOGOUT ────────────────────────────────────────────────────

    /**
     * Logout still reads the raw Authorization token because the Redis blacklist
     * stores the token string itself (not userId). This is the ONLY place the
     * controller touches the token directly.
     */
    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout — blacklists the current JWT in Redis")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or already expired", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String bearerToken) {

        String token = resolveToken(bearerToken);
        authService.logout(token);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }

    // ── REFRESH TOKEN ─────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using a valid refresh token")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New access + refresh tokens returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token invalid or expired", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {

        AuthResponse auth = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", auth));
    }

    // ── PROFILE GET ───────────────────────────────────────────────

    @GetMapping("/profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the current authenticated user's profile")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId) {

        UserProfileResponse profile = authService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched", profile));
    }

    // ── PROFILE UPDATE ────────────────────────────────────────────

    @PutMapping("/profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update the current user's profile (name, username, bio, avatar)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserProfileResponse updated = authService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", updated));
    }

    // ── CHANGE PASSWORD ───────────────────────────────────────────

    @PutMapping("/password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change password (requires current password verification)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Current password incorrect", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ChangePasswordRequest request) {

        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully"));
    }

    // ── SEARCH USERS ──────────────────────────────────────────────

    @GetMapping("/search")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Search users by username keyword for DM or group addition")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> searchUsers(
            @RequestParam String username) {

        List<UserProfileResponse> results = authService.searchUsers(username);
        return ResponseEntity.ok(ApiResponse.ok("Search results", results));
    }

    // ── UPDATE STATUS ─────────────────────────────────────────────

    @PutMapping("/status")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update online status: ONLINE / AWAY / DND / INVISIBLE")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status value", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateStatus(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateStatusRequest request) {

        UserProfileResponse updated = authService.updateStatus(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Status updated", updated));
    }

    // ── FORGOT PASSWORD ───────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Send a password reset email (15-minute token stored in Redis)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reset email sent (always 200 to prevent email enumeration)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        authService.forgotPassword(request);
        // Always 200 — never reveal if email exists
        return ResponseEntity.ok(ApiResponse.ok(
                "If that email exists, a reset link has been sent."));
    }

    // ── RESET PASSWORD ────────────────────────────────────────────

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using the token received by email")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Token invalid or expired", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful. Please login."));
    }

    // ── CHECK USERNAME AVAILABILITY ────────────────────────────────

    @GetMapping("/check-username")
    @Operation(summary = "Check if a username is available (real-time, no auth required)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Availability flag returned (true = available)")
    })
    public ResponseEntity<ApiResponse<Boolean>> checkUsername(
            @RequestParam String username) {

        boolean available = authService.checkUsernameAvailability(username);
        String msg = available ? "Username is available" : "Username is already taken";
        return ResponseEntity.ok(ApiResponse.ok(msg, available));
    }

    // ── GET USER BY ID (inter-service use) ────────────────────────

    @GetMapping("/users/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a user's public profile by userId (used by other microservices)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @PathVariable String userId) {

        UserProfileResponse profile = authService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.ok("User profile", profile));
    }

    // ── PLATFORM ADMIN ────────────────────────────────────────────

    @GetMapping("/admin/users")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List all platform users (admin only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a platform admin", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getAllUsers(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String requesterId) {

        return ResponseEntity.ok(ApiResponse.ok("All users", authService.getAllUsers(requesterId)));
    }

    @PatchMapping("/admin/users/{targetId}/toggle")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Toggle a user's active/suspended state (admin only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User state toggled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a platform admin", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> toggleUser(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String requesterId,
            @PathVariable String targetId) {

        return ResponseEntity.ok(ApiResponse.ok(
                "User status toggled", authService.toggleUserActive(requesterId, targetId)));
    }

    @DeleteMapping("/admin/users/{targetId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Hard-delete a user account (admin only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a platform admin", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String requesterId,
            @PathVariable String targetId) {

        authService.adminDeleteUser(requesterId, targetId);
        return ResponseEntity.ok(ApiResponse.ok("User deleted"));
    }

    // ── REPORTS ───────────────────────────────────────────────────

    /**
     * POST /auth/reports
     * Any authenticated user can submit a report against another user.
     * The reporter's userId is taken from the X-User-Id header (set by the gateway).
     */
    @PostMapping("/reports")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Submit a report against another user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Report submitted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserReportResponse>> submitReport(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String reporterId,
            @Valid @RequestBody SubmitReportRequest request) {

        UserReportResponse report = authService.submitReport(reporterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Report submitted", report));
    }

    /**
     * GET /auth/admin/reports
     * Admin — list all reports, newest first.
     */
    @GetMapping("/admin/reports")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Admin — list all user reports")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Report list returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a platform admin", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<UserReportResponse>>> getReports(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String requesterId) {

        return ResponseEntity.ok(
                ApiResponse.ok("Reports fetched", authService.getAllReports(requesterId)));
    }

    /**
     * PATCH /auth/admin/reports/{reportId}/status?action=RESOLVED|DISMISSED
     * Admin — mark a report as resolved or dismissed.
     */
    @PatchMapping("/admin/reports/{reportId}/status")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Admin — update report status (RESOLVED | DISMISSED)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Report status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a platform admin", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Report not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserReportResponse>> updateReportStatus(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String requesterId,
            @PathVariable String reportId,
            @RequestParam String action) {

        UserReportResponse updated = authService.updateReportStatus(requesterId, reportId, action);
        return ResponseEntity.ok(ApiResponse.ok("Report updated", updated));
    }

    // ── PRIVATE HELPER ────────────────────────────────────────────


    private String resolveToken(String bearerToken) {
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }

    // ── INTERNAL: called by payment-service (service-to-service) ──────

    /**
     * PUT /auth/internal/upgrade-plan/{userId}
     *
     * Called directly by payment-service at http://localhost:8081 after a verified
     * Razorpay payment. NOT routed through the API Gateway — there is no JWT on this
     * request. The endpoint is safe because port 8081 is network-isolated (not
     * exposed to the internet via the gateway).
     *
     * Returns a fresh AuthResponse (new access + refresh token) with the updated
     * plan claim baked in, so the frontend can swap its JWT immediately.
     */
    @PutMapping("/internal/upgrade-plan/{userId}")
    @Operation(summary = "Internal — upgrade a user's plan after verified payment",
               description = "Called by payment-service directly on port 8081. Not reachable through the public API Gateway.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan upgraded; fresh JWT returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> upgradePlan(
            @PathVariable String userId,
            @RequestParam String plan) {

        return ResponseEntity.ok(ApiResponse.ok(
                "Plan upgraded to " + plan, authService.upgradePlan(userId, plan)));
    }
}
