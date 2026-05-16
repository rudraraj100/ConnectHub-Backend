package com.auth_service.service.impl;

import com.auth_service.dto.request.*;
import com.auth_service.dto.response.*;
import com.auth_service.entity.*;
import com.auth_service.messaging.AuthEventPublisher;
import com.auth_service.repository.UserRepository;
import com.auth_service.repository.UserReportRepository;
import com.auth_service.security.JwtUtil;
import com.auth_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AuthServiceImpl is where the core security logic lives.
 * It handles password hashing, JWT generation, and interaction with MySQL and Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository       userRepository;
    private final UserReportRepository  userReportRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtUtil              jwtUtil;
    private final StringRedisTemplate  redisTemplate;
    private final AuthenticationManager authenticationManager;
    private final JavaMailSender       mailSender;
    private final AuthEventPublisher   eventPublisher;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.email.verify-token-ttl-minutes:15}")
    private long verifyTokenTtlMin;

    // ── Redis key prefixes ────────────────────────────────────────────
    private static final String BLACKLIST_PREFIX    = "blacklist:";
    private static final String RESET_TOKEN_PREFIX  = "pwd_reset:";
    private static final String VERIFY_OTP_PREFIX   = "email_verify_otp:";  // key = email
    private static final long   RESET_TOKEN_TTL_MIN = 15;

    private static final java.util.Random OTP_RANDOM = new java.util.Random();

    /**
     * Handles the user registration process.
     * 1. Checks for duplicate emails/usernames.
     * 2. Hashes the password using BCrypt.
     * 3. Saves the user as 'inactive'.
     * 4. Generates and stores an OTP in Redis for email verification.
     */
    @Override
    public String register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username is already taken.");
        }

        // Create user — NOT active, NOT email-verified until link is clicked
        User user = User.builder()
                .email(req.getEmail().toLowerCase().trim())
                .username(req.getUsername().trim())
                .fullName(req.getFullName().trim())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .provider(AuthProvider.LOCAL)
                .status(UserStatus.ONLINE)
                .isActive(false)          // locked until verified
                .emailVerified(false)
                .country(req.getCountry())
                .city(req.getCity())
                .countryCode(req.getCountryCode())
                .phoneNumber(req.getPhoneNumber())
                .build();

        user = userRepository.save(user);
        log.info("New user registered (pending verification): {}", user.getEmail());

        // Generate 6-digit OTP and store in Redis (keyed by email, TTL = 15 min)
        String otp = String.format("%06d", 100000 + OTP_RANDOM.nextInt(900000));
        redisTemplate.opsForValue().set(
                VERIFY_OTP_PREFIX + user.getEmail().toLowerCase().trim(),
                otp,
                verifyTokenTtlMin, TimeUnit.MINUTES
        );

        // Send OTP email
        try {
            sendOtpEmail(user.getEmail(), user.getFullName(), otp);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", user.getEmail(), e.getMessage());
            // Don't fail registration if mail fails — user can use resend
        }

        return "Registration successful! Please enter the 6-digit OTP sent to your email (valid for "
                + verifyTokenTtlMin + " minutes).";
    }

    // ═══════════════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════════════

    @Override
    public AuthResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        } catch (DisabledException ex) {
            throw new DisabledException("Your account has been suspended. Please contact support.");
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found."));

        if (!user.isActive()) {
            throw new DisabledException("Your account has been suspended. Please contact support.");
        }

        // Email verification gate — LOCAL users only (OAuth2 users are pre-verified)
        if (!user.isEmailVerified()) {
            throw new DisabledException("EMAIL_NOT_VERIFIED");
        }

        user.setStatus(UserStatus.ONLINE);
        userRepository.save(user);

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // ═══════════════════════════════════════════════════════════════
    // OTP VERIFICATION  (replaces token-link approach)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void verifyEmail(String email, String otp) {
        String normalised = email.toLowerCase().trim();
        String redisKey   = VERIFY_OTP_PREFIX + normalised;
        String storedOtp  = redisTemplate.opsForValue().get(redisKey);

        if (storedOtp == null) {
            throw new IllegalArgumentException(
                    "OTP has expired or is invalid. Please request a new one.");
        }
        if (!storedOtp.equals(otp.trim())) {
            throw new IllegalArgumentException("Incorrect OTP. Please try again.");
        }

        User user = userRepository.findByEmail(normalised)
                .orElseThrow(() -> new NoSuchElementException("User not found."));

        if (user.isEmailVerified()) {
            redisTemplate.delete(redisKey);
            return;
        }

        user.setEmailVerified(true);
        user.setActive(true);
        userRepository.save(user);
        redisTemplate.delete(redisKey);

        log.info("Email verified via OTP for: {}", normalised);
        eventPublisher.publishUserVerified(user.getUserId(), user.getEmail(), user.getFullName());
    }

    @Override
    public void resendVerification(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            log.info("Resend OTP requested for unknown email: {} — ignoring.", email);
            return;
        }
        User user = userOpt.get();
        if (user.isEmailVerified()) {
            log.info("Resend OTP ignored — email already verified: {}", email);
            return;
        }

        String otp = String.format("%06d", 100000 + OTP_RANDOM.nextInt(900000));
        redisTemplate.opsForValue().set(
                VERIFY_OTP_PREFIX + user.getEmail().toLowerCase().trim(),
                otp,
                verifyTokenTtlMin, TimeUnit.MINUTES
        );
        try {
            sendOtpEmail(user.getEmail(), user.getFullName(), otp);
            log.info("OTP email resent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to resend OTP email to {}: {}", email, e.getMessage());
            throw new IllegalStateException("Could not send email. Please try again later.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LOGOUT — blacklist the JWT in Redis
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) return;

        Date expiry = jwtUtil.extractExpiration(token);
        long ttlMs  = expiry.getTime() - System.currentTimeMillis();

        if (ttlMs > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token, "1", ttlMs, TimeUnit.MILLISECONDS);
            log.info("JWT blacklisted — TTL: {}ms", ttlMs);
        }

        // Update last-seen on logout
        try {
            String userId = jwtUtil.extractUserId(token);
            userRepository.findById(userId).ifPresent(user -> {
                user.setLastSeenAt(LocalDateTime.now());
                user.setStatus(UserStatus.INVISIBLE);
                userRepository.save(user);
            });
        } catch (Exception e) {
            log.warn("Could not update last-seen on logout: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // REFRESH TOKEN
    // ═══════════════════════════════════════════════════════════════

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Refresh token is invalid or expired.");
        }

        String email  = jwtUtil.extractEmail(refreshToken);
        String userId = jwtUtil.extractUserId(refreshToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        // CRITICAL: a suspended user must NOT receive a new JWT.
        // Without this check, a suspended account holder who already has a
        // live refresh token can keep calling /auth/refresh to bypass suspension.
        if (!user.isActive()) {
            throw new DisabledException("Your account has been suspended. Please contact support.");
        }

        return buildAuthResponse(user);
    }

    // ═══════════════════════════════════════════════════════════════
    // GET USER BY ID
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        return toProfileResponse(user);
    }

    // ═══════════════════════════════════════════════════════════════
    // UPDATE PROFILE
    // ═══════════════════════════════════════════════════════════════

    @Override
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found."));

        // Core identity
        if (req.getFullName()  != null) user.setFullName(req.getFullName().trim());
        if (req.getBio()       != null) user.setBio(req.getBio().trim());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl().trim());

        // Username — check uniqueness if changed
        if (req.getUsername() != null) {
            String newUsername = req.getUsername().trim();
            if (!newUsername.equals(user.getUsername()) &&
                    userRepository.existsByUsername(newUsername)) {
                throw new IllegalArgumentException("Username '" + newUsername + "' is already taken.");
            }
            user.setUsername(newUsername);
        }

        // Location & contact — update existing row in-place
        if (req.getCountry()     != null) user.setCountry(req.getCountry().trim());
        if (req.getCity()        != null) user.setCity(req.getCity().trim());
        if (req.getCountryCode() != null) user.setCountryCode(req.getCountryCode().trim());
        if (req.getPhoneNumber() != null) user.setPhoneNumber(req.getPhoneNumber().trim());

        // Custom status — only PREMIUM users may set it
        if (req.getCustomStatus() != null
                && user.getPlan() != null
                && user.getPlan().name().equals("PREMIUM")) {
            String cs = req.getCustomStatus().trim();
            user.setCustomStatus(cs.isBlank() ? null : cs);  // blank = clear the status
        }

        log.info("Profile updated for user: {}", user.getEmail());
        return toProfileResponse(userRepository.save(user));
    }

    // ═══════════════════════════════════════════════════════════════
    // CHANGE PASSWORD
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void changePassword(String userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found."));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
    }

    // ═══════════════════════════════════════════════════════════════
    // SEARCH USERS
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<UserProfileResponse> searchUsers(String keyword) {
        return userRepository.searchByUsername(keyword)
                .stream()
                .map(this::toProfileResponse)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // UPDATE STATUS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public UserProfileResponse updateStatus(String userId, UpdateStatusRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found."));

        user.setStatus(req.getStatus());
        log.info("Status updated to {} for user: {}", req.getStatus(), user.getEmail());
        // Note: PRESENCE_UPDATE STOMP event is broadcast by the WebSocket handler
        // when it detects the change — §4.1 "status broadcast as PRESENCE_UPDATE"
        return toProfileResponse(userRepository.save(user));
    }

    // ═══════════════════════════════════════════════════════════════
    // CHECK USERNAME AVAILABILITY
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public boolean checkUsernameAvailability(String username) {
        return !userRepository.existsByUsername(username);
    }

    // ═══════════════════════════════════════════════════════════════
    // FORGOT PASSWORD
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void forgotPassword(ForgotPasswordRequest req) {
        // Security best practice: never reveal whether an email is registered.
        // If the email doesn't exist, we silently return without doing anything.
        Optional<User> userOpt = userRepository.findByEmail(req.getEmail());
        if (userOpt.isEmpty()) {
            log.info("Forgot-password requested for unknown email: {} — ignoring silently.",
                    req.getEmail());
            return; // Return 200 to the caller; do NOT throw.
        }

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();

        try {
            redisTemplate.opsForValue().set(
                    RESET_TOKEN_PREFIX + token,
                    user.getUserId(),
                    RESET_TOKEN_TTL_MIN, TimeUnit.MINUTES
            );
            sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
            log.info("Password reset email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
            throw new IllegalStateException(
                    "Could not send reset email. Please try again later.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESET PASSWORD — validate Redis token, update password
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void resetPassword(ResetPasswordRequest req) {
        String redisKey = RESET_TOKEN_PREFIX + req.getToken();
        String userId   = redisTemplate.opsForValue().get(redisKey);

        if (userId == null) {
            throw new IllegalArgumentException(
                    "Reset link is invalid or has expired. Please request a new one.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found."));

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // Token consumed — delete it
        redisTemplate.delete(redisKey);
        log.info("Password reset successfully for user: {}", user.getEmail());
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private AuthResponse buildAuthResponse(User user) {
        String token        = jwtUtil.generateToken(user.getUserId(), user.getEmail(),
                                                    user.getPlan() != null ? user.getPlan().name() : "FREE");
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMs())
                .user(toProfileResponse(user))
                .build();
    }

    private UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .status(user.getStatus())
                .provider(user.getProvider())
                .isActive(user.isActive())
                .role(user.getRole())
                .plan(user.getPlan())
                .customStatus(user.getCustomStatus())
                .lastSeenAt(user.getLastSeenAt())
                .createdAt(user.getCreatedAt())
                .country(user.getCountry())
                .city(user.getCity())
                .countryCode(user.getCountryCode())
                .phoneNumber(user.getPhoneNumber())
                .build();
    }

    private void sendPasswordResetEmail(String toEmail, String name, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        String displayName = (name != null && !name.isBlank()) ? name : toEmail;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailFrom);
        msg.setTo(toEmail);
        msg.setSubject("ConnectHub — Password Reset Request");
        msg.setText(
                "Hi " + displayName + ",\n\n" +
                "You requested a password reset for your ConnectHub account.\n\n" +
                "Click the link below to reset your password (expires in 15 minutes):\n\n" +
                resetLink + "\n\n" +
                "If you did not request this, please ignore this email.\n\n" +
                "— The ConnectHub Team"
        );
        mailSender.send(msg);
    }

    private void sendOtpEmail(String toEmail, String name, String otp) {
        String displayName = (name != null && !name.isBlank()) ? name : toEmail;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailFrom);
        msg.setTo(toEmail);
        msg.setSubject("ConnectHub — Your Verification Code: " + otp);
        msg.setText(
                "Hi " + displayName + ",\n\n" +
                "Welcome to ConnectHub!\n\n" +
                "Your email verification code is:\n\n" +
                "    " + otp + "\n\n" +
                "Enter this code in the app to verify your email address.\n" +
                "This code expires in " + verifyTokenTtlMin + " minutes.\n\n" +
                "If you did not create a ConnectHub account, please ignore this email.\n\n" +
                "— The ConnectHub Team"
        );
        mailSender.send(msg);
    }

    // ═══════════════════════════════════════════════════════════════
    // PLATFORM ADMIN
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<UserProfileResponse> getAllUsers(String requesterId) {
        assertAdmin(requesterId);
        return userRepository.findAll().stream()
                .map(this::toProfileResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserProfileResponse toggleUserActive(String requesterId, String targetId) {
        assertAdmin(requesterId);
        if (requesterId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot suspend your own admin account.");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + targetId));

        boolean newActiveState = !target.isActive();

        // Direct native SQL UPDATE — bypasses Lombok/JPA dirty-tracking entirely.
        // This is the guaranteed-to-persist path for the is_active column.
        userRepository.setIsActive(targetId, newActiveState);

        // Redis session revocation for immediate mid-session enforcement
        if (!newActiveState) {
            redisTemplate.opsForValue().set(
                    "suspended:" + targetId, "1", 24, TimeUnit.HOURS);
            log.info("[Admin] User {} suspended — Redis revocation key set", target.getEmail());
        } else {
            redisTemplate.delete("suspended:" + targetId);
            log.info("[Admin] User {} reinstated — Redis revocation key cleared", target.getEmail());
        }

        log.info("[Admin] {} toggled user {} → isActive={}",
                requesterId, target.getEmail(), newActiveState);

        // Re-fetch from DB to get the authoritative persisted state
        User updated = userRepository.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException("User not found after update: " + targetId));
        return toProfileResponse(updated);
    }

    @Override
    public void adminDeleteUser(String requesterId, String targetId) {
        assertAdmin(requesterId);
        if (requesterId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot delete your own admin account.");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + targetId));
        userRepository.deleteByUserId(targetId);
        log.info("[Admin] {} deleted user {}", requesterId, target.getEmail());
    }

    /** Verifies that requester has PLATFORM_ADMIN role in the DB. */
    private void assertAdmin(String requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new NoSuchElementException("Requester not found."));
        if (requester.getRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new IllegalArgumentException("Platform admin access required.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL — called by payment-service after verified payment
    // ═══════════════════════════════════════════════════════════════

    @Override
    public AuthResponse upgradePlan(String userId, String plan) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        PlanType planType;
        try {
            planType = PlanType.valueOf(plan.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid plan: " + plan + ". Must be FREE or PREMIUM.");
        }

        user.setPlan(planType);
        userRepository.save(user);
        log.info("[Plan] User {} upgraded to plan {}", user.getEmail(), planType);

        // Publish RabbitMQ event → notification-service sends premium notification
        if (planType == PlanType.PREMIUM) {
            eventPublisher.publishPlanUpgraded(user.getUserId(), user.getEmail(), user.getFullName());
        }

        // Issue a fresh JWT so the frontend can start using premium features immediately
        return buildAuthResponse(user);
    }

    // ═══════════════════════════════════════════════════════════════
    // REPORTS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public UserReportResponse submitReport(String reporterId, SubmitReportRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new NoSuchElementException("Reporter not found."));

        User reported = userRepository.findByUsername(request.getReportedUsername().trim())
                .orElseThrow(() -> new NoSuchElementException(
                        "User '" + request.getReportedUsername() + "' not found."));

        if (reporter.getUserId().equals(reported.getUserId())) {
            throw new IllegalArgumentException("You cannot report yourself.");
        }

        UserReport report = UserReport.builder()
                .reporterId(reporterId)
                .reporterUsername(reporter.getUsername())
                .reportedUserId(reported.getUserId())
                .reportedUsername(reported.getUsername())
                .reason(request.getReason())
                .details(request.getDetails())
                .status(UserReport.ReportStatus.PENDING)
                .build();

        report = userReportRepository.save(report);
        log.info("[Report] {} reported {} for: {}",
                reporter.getUsername(), reported.getUsername(), request.getReason());
        return UserReportResponse.from(report);
    }

    @Override
    public List<UserReportResponse> getAllReports(String requesterId) {
        assertAdmin(requesterId);
        return userReportRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(UserReportResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public UserReportResponse updateReportStatus(String requesterId, String reportId, String action) {
        assertAdmin(requesterId);

        UserReport report = userReportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Report not found: " + reportId));

        UserReport.ReportStatus newStatus;
        try {
            newStatus = UserReport.ReportStatus.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid action. Must be RESOLVED or DISMISSED.");
        }
        if (newStatus == UserReport.ReportStatus.PENDING) {
            throw new IllegalArgumentException("Cannot set status back to PENDING.");
        }

        report.setStatus(newStatus);
        report = userReportRepository.save(report);
        log.info("[Admin] Report {} marked as {} by {}", reportId, newStatus, requesterId);
        return UserReportResponse.from(report);
    }
}
