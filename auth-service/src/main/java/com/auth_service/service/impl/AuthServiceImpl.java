package com.auth_service.service.impl;

import com.auth_service.dto.request.*;
import com.auth_service.dto.response.*;
import com.auth_service.entity.*;
import com.auth_service.repository.UserRepository;
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
 * AuthServiceImpl — §4.1 business logic implementation.
 * Handles register, login, logout (JWT blacklist), refresh token,
 * profile CRUD, password change, user search, status update,
 * forgot-password (email), and reset-password (Redis token).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final JwtUtil             jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final AuthenticationManager authenticationManager;
    private final JavaMailSender      mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String mailFrom;

    // ── Redis key prefixes ────────────────────────────────────────
    private static final String BLACKLIST_PREFIX   = "blacklist:";
    private static final String RESET_TOKEN_PREFIX = "pwd_reset:";
    private static final long   RESET_TOKEN_TTL_MIN = 15;

    // ═══════════════════════════════════════════════════════════════
    // REGISTER
    // ═══════════════════════════════════════════════════════════════

    @Override
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username is already taken.");
        }

        User user = User.builder()
                .email(req.getEmail().toLowerCase().trim())
                .username(req.getUsername().trim())
                .fullName(req.getFullName().trim())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .provider(AuthProvider.LOCAL)
                .status(UserStatus.ONLINE)
                .isActive(true)
                .country(req.getCountry())
                .city(req.getCity())
                .countryCode(req.getCountryCode())
                .phoneNumber(req.getPhoneNumber())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    // ═══════════════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════════════

    @Override
    public AuthResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("User not found."));

        if (!user.isActive()) {
            throw new DisabledException("Your account has been suspended.");
        }

        user.setStatus(UserStatus.ONLINE);
        userRepository.save(user);

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
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
        String token        = jwtUtil.generateToken(user.getUserId(), user.getEmail());
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
}
