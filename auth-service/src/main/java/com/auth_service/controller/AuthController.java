package com.auth_service.controller;

import com.auth_service.dto.request.*;
import com.auth_service.dto.response.*;
import com.auth_service.security.JwtUtil;
import com.auth_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AuthController — §4.1 AuthResource REST endpoints.
 * Exposes: /auth/register, /auth/login, /auth/logout, /auth/refresh,
 *          /auth/profile (GET/PUT), /auth/password, /auth/search,
 *          /auth/status, /auth/forgot-password, /auth/reset-password
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User identity, JWT, OAuth2, profile and status")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil     jwtUtil;

    // ── REGISTER ──────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new user with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse auth = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful", auth));
    }

    // ── LOGIN ─────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login with email and password — returns JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", auth));
    }

    // ── LOGOUT ────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Logout — blacklists the current JWT in Redis")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String bearerToken) {

        String token = resolveToken(bearerToken);
        authService.logout(token);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }

    // ── REFRESH TOKEN ─────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using a valid refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {

        AuthResponse auth = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", auth));
    }

    // ── PROFILE GET ───────────────────────────────────────────────

    @GetMapping("/profile")
    @Operation(summary = "Get the current authenticated user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @RequestHeader("Authorization") String bearerToken) {

        String userId = jwtUtil.extractUserId(resolveToken(bearerToken));
        UserProfileResponse profile = authService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched", profile));
    }

    // ── PROFILE UPDATE ────────────────────────────────────────────

    @PutMapping("/profile")
    @Operation(summary = "Update the current user's profile (name, username, bio, avatar)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody UpdateProfileRequest request) {

        String userId = jwtUtil.extractUserId(resolveToken(bearerToken));
        UserProfileResponse updated = authService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", updated));
    }

    // ── CHANGE PASSWORD ───────────────────────────────────────────

    @PutMapping("/password")
    @Operation(summary = "Change password (requires current password verification)")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody ChangePasswordRequest request) {

        String userId = jwtUtil.extractUserId(resolveToken(bearerToken));
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully"));
    }

    // ── SEARCH USERS ──────────────────────────────────────────────

    @GetMapping("/search")
    @Operation(summary = "Search users by username keyword for DM or group addition")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> searchUsers(
            @RequestParam String username) {

        List<UserProfileResponse> results = authService.searchUsers(username);
        return ResponseEntity.ok(ApiResponse.ok("Search results", results));
    }

    // ── UPDATE STATUS ─────────────────────────────────────────────

    @PutMapping("/status")
    @Operation(summary = "Update online status: ONLINE / AWAY / DND / INVISIBLE")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateStatus(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody UpdateStatusRequest request) {

        String userId = jwtUtil.extractUserId(resolveToken(bearerToken));
        UserProfileResponse updated = authService.updateStatus(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Status updated", updated));
    }

    // ── FORGOT PASSWORD ───────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Send a password reset email (15-minute token stored in Redis)")
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
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful. Please login."));
    }

    // ── CHECK USERNAME AVAILABILITY ────────────────────────────────

    @GetMapping("/check-username")
    @Operation(summary = "Check if a username is available (real-time, no auth required)")
    public ResponseEntity<ApiResponse<Boolean>> checkUsername(
            @RequestParam String username) {

        boolean available = authService.checkUsernameAvailability(username);
        String msg = available ? "Username is available" : "Username is already taken";
        return ResponseEntity.ok(ApiResponse.ok(msg, available));
    }

    // ── GET USER BY ID (inter-service use) ────────────────────────

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get a user's public profile by userId (used by other microservices)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @PathVariable String userId) {

        UserProfileResponse profile = authService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.ok("User profile", profile));
    }

    // ── PRIVATE HELPER ────────────────────────────────────────────

    private String resolveToken(String bearerToken) {
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }
}
