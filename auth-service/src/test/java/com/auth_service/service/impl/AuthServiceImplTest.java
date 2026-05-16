package com.auth_service.service.impl;

import com.auth_service.dto.request.*;
import com.auth_service.entity.*;
import com.auth_service.messaging.AuthEventPublisher;
import com.auth_service.repository.UserRepository;
import com.auth_service.repository.UserReportRepository;
import com.auth_service.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — unit tests")
class AuthServiceImplTest {

    @Mock UserRepository        userRepository;
    @Mock UserReportRepository  userReportRepository;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock JwtUtil               jwtUtil;
    @Mock StringRedisTemplate   redisTemplate;
    @Mock AuthenticationManager authenticationManager;
    @Mock JavaMailSender        mailSender;
    @Mock AuthEventPublisher    eventPublisher;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks AuthServiceImpl sut;

    private User activeUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "frontendUrl",       "http://localhost:4200");
        ReflectionTestUtils.setField(sut, "mailFrom",          "no-reply@connecthub.com");
        ReflectionTestUtils.setField(sut, "verifyTokenTtlMin", 15L);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        activeUser = User.builder()
                .userId("user-1").email("alice@example.com").username("alice")
                .fullName("Alice Smith").passwordHash("hashed")
                .isActive(true).emailVerified(true)
                .status(UserStatus.ONLINE).provider(AuthProvider.LOCAL)
                .role(PlatformRole.MEMBER).plan(PlanType.FREE).build();

        adminUser = User.builder()
                .userId("admin-1").email("admin@example.com").username("admin")
                .fullName("Admin User").passwordHash("hashed")
                .isActive(true).emailVerified(true)
                .status(UserStatus.ONLINE).provider(AuthProvider.LOCAL)
                .role(PlatformRole.PLATFORM_ADMIN).plan(PlanType.FREE).build();
    }

    // ── register ──────────────────────────────────────────────────────

    @Test
    @DisplayName("register() — throws when email already exists")
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);
        RegisterRequest req = buildRegisterRequest();
        assertThatThrownBy(() -> sut.register(req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("register() — throws when username already taken")
    void register_duplicateUsername_throws() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        RegisterRequest req = buildRegisterRequest();
        assertThatThrownBy(() -> sut.register(req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("register() — saves user, stores OTP in Redis, sends email")
    void register_success() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(activeUser);

        String result = sut.register(buildRegisterRequest());

        assertThat(result).containsIgnoringCase("OTP");
        verify(userRepository).save(any(User.class));
        verify(valueOps).set(startsWith("email_verify_otp:"), anyString(), anyLong(), any());
    }

    // ── verifyEmail ───────────────────────────────────────────────────

    @Test
    @DisplayName("verifyEmail() — throws when OTP not in Redis")
    void verifyEmail_otpExpired_throws() {
        when(valueOps.get("email_verify_otp:alice@example.com")).thenReturn(null);
        assertThatThrownBy(() -> sut.verifyEmail("alice@example.com", "123456"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expired");
    }

    @Test
    @DisplayName("verifyEmail() — throws when OTP is wrong")
    void verifyEmail_wrongOtp_throws() {
        when(valueOps.get("email_verify_otp:alice@example.com")).thenReturn("654321");
        assertThatThrownBy(() -> sut.verifyEmail("alice@example.com", "111111"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Incorrect");
    }

    @Test
    @DisplayName("verifyEmail() — no-op when already verified")
    void verifyEmail_alreadyVerified_noop() {
        when(valueOps.get("email_verify_otp:alice@example.com")).thenReturn("123456");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));

        sut.verifyEmail("alice@example.com", "123456");

        verify(userRepository, never()).save(any());
        verify(redisTemplate).delete("email_verify_otp:alice@example.com");
    }

    @Test
    @DisplayName("verifyEmail() — activates user and publishes event on success")
    void verifyEmail_success() {
        activeUser.setEmailVerified(false);
        activeUser.setActive(false);
        when(valueOps.get("email_verify_otp:alice@example.com")).thenReturn("123456");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenReturn(activeUser);

        sut.verifyEmail("alice@example.com", "123456");

        assertThat(activeUser.isEmailVerified()).isTrue();
        assertThat(activeUser.isActive()).isTrue();
        verify(eventPublisher).publishUserVerified(any(), any(), any());
    }

    // ── login ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("login() — throws when credentials are bad")
    void login_badCredentials_throws() {
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());
        LoginRequest badReq = new LoginRequest();
        badReq.setEmail("alice@example.com");
        badReq.setPassword("wrong");
        assertThatThrownBy(() -> sut.login(badReq))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login() — throws when email not verified")
    void login_emailNotVerified_throws() {
        activeUser.setEmailVerified(false);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("pass");
        assertThatThrownBy(() -> sut.login(req))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("login() — returns tokens on success")
    void login_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenReturn(activeUser);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("access_token");
        when(jwtUtil.generateRefreshToken(any(), any())).thenReturn("refresh_token");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("alice@example.com");
        loginReq.setPassword("Pass1@word");
        var resp = sut.login(loginReq);

        assertThat(resp.getToken()).isEqualTo("access_token");
    }

    // ── logout ────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout() — no-op for null token")
    void logout_nullToken_noop() {
        sut.logout(null);
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("logout() — blacklists valid token in Redis")
    void logout_validToken_blacklists() {
        Date future = new Date(System.currentTimeMillis() + 60_000);
        when(jwtUtil.extractExpiration("valid.jwt")).thenReturn(future);
        when(jwtUtil.extractUserId("valid.jwt")).thenReturn("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenReturn(activeUser);

        sut.logout("valid.jwt");

        verify(valueOps).set(startsWith("blacklist:"), eq("1"), anyLong(), any());
    }

    // ── refreshToken ──────────────────────────────────────────────────

    @Test
    @DisplayName("refreshToken() — throws when token invalid")
    void refreshToken_invalid_throws() {
        when(jwtUtil.validateToken("bad")).thenReturn(false);
        assertThatThrownBy(() -> sut.refreshToken("bad")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refreshToken() — throws when user suspended")
    void refreshToken_suspended_throws() {
        activeUser.setActive(false);
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.extractEmail("token")).thenReturn("alice@example.com");
        when(jwtUtil.extractUserId("token")).thenReturn("user-1");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> sut.refreshToken("token")).isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("refreshToken() — returns new tokens on success")
    void refreshToken_success() {
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.extractEmail("token")).thenReturn("alice@example.com");
        when(jwtUtil.extractUserId("token")).thenReturn("user-1");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("new_access");
        when(jwtUtil.generateRefreshToken(any(), any())).thenReturn("new_refresh");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        var resp = sut.refreshToken("token");
        assertThat(resp.getToken()).isEqualTo("new_access");
    }

    // ── getUserById ───────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById() — throws when user not found")
    void getUserById_notFound_throws() {
        when(userRepository.findById("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.getUserById("bad")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("getUserById() — returns profile response")
    void getUserById_success() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        var resp = sut.getUserById("user-1");
        assertThat(resp.getEmail()).isEqualTo("alice@example.com");
    }

    // ── updateProfile ─────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile() — throws when username already taken by another user")
    void updateProfile_duplicateUsername_throws() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByUsername("bob")).thenReturn(true);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("bob");

        assertThatThrownBy(() -> sut.updateProfile("user-1", req))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("taken");
    }

    @Test
    @DisplayName("updateProfile() — updates fields and saves")
    void updateProfile_success() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByUsername("alice_new")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(activeUser);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("Alice New");
        req.setUsername("alice_new");
        req.setBio("Hi!");

        sut.updateProfile("user-1", req);

        assertThat(activeUser.getFullName()).isEqualTo("Alice New");
        assertThat(activeUser.getBio()).isEqualTo("Hi!");
    }

    // ── changePassword ────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword() — throws when current password wrong")
    void changePassword_wrongCurrent_throws() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrong");
        req.setNewPassword("NewPass1@");

        assertThatThrownBy(() -> sut.changePassword("user-1", req))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("changePassword() — encodes and saves new password")
    void changePassword_success() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Old1@pass", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("New1@pass")).thenReturn("new_hashed");
        when(userRepository.save(any())).thenReturn(activeUser);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("Old1@pass");
        req.setNewPassword("New1@pass");

        sut.changePassword("user-1", req);

        assertThat(activeUser.getPasswordHash()).isEqualTo("new_hashed");
    }

    // ── checkUsernameAvailability ─────────────────────────────────────

    @Test
    @DisplayName("checkUsernameAvailability() — returns true when available")
    void checkUsername_available() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        assertThat(sut.checkUsernameAvailability("newuser")).isTrue();
    }

    @Test
    @DisplayName("checkUsernameAvailability() — returns false when taken")
    void checkUsername_taken() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        assertThat(sut.checkUsernameAvailability("alice")).isFalse();
    }

    // ── searchUsers ───────────────────────────────────────────────────

    @Test
    @DisplayName("searchUsers() — returns mapped list")
    void searchUsers_returnsList() {
        when(userRepository.searchByUsername("ali")).thenReturn(List.of(activeUser));
        var result = sut.searchUsers("ali");
        assertThat(result).hasSize(1).first().extracting("username").isEqualTo("alice");
    }

    // ── updateStatus ──────────────────────────────────────────────────

    @Test
    @DisplayName("updateStatus() — updates user status")
    void updateStatus_success() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenReturn(activeUser);

        UpdateStatusRequest req = new UpdateStatusRequest();
        req.setStatus(UserStatus.AWAY);

        sut.updateStatus("user-1", req);

        assertThat(activeUser.getStatus()).isEqualTo(UserStatus.AWAY);
    }

    // ── forgotPassword ────────────────────────────────────────────────

    @Test
    @DisplayName("forgotPassword() — silent no-op for unknown email")
    void forgotPassword_unknownEmail_silent() {
        when(userRepository.findByEmail("unknown@x.com")).thenReturn(Optional.empty());
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("unknown@x.com");

        sut.forgotPassword(req); // should not throw

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("forgotPassword() — stores token and sends email")
    void forgotPassword_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser));

        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("alice@example.com");

        sut.forgotPassword(req);

        verify(valueOps).set(startsWith("pwd_reset:"), eq("user-1"), anyLong(), any());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── resetPassword ─────────────────────────────────────────────────

    @Test
    @DisplayName("resetPassword() — throws when token invalid/expired")
    void resetPassword_invalidToken_throws() {
        when(valueOps.get("pwd_reset:bad-token")).thenReturn(null);
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("bad-token");
        req.setNewPassword("NewPass1@");
        assertThatThrownBy(() -> sut.resetPassword(req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resetPassword() — updates password and deletes token")
    void resetPassword_success() {
        when(valueOps.get("pwd_reset:valid-token")).thenReturn("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.encode("New1@pass")).thenReturn("new_hashed");
        when(userRepository.save(any())).thenReturn(activeUser);

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("valid-token");
        req.setNewPassword("New1@pass");

        sut.resetPassword(req);

        assertThat(activeUser.getPasswordHash()).isEqualTo("new_hashed");
        verify(redisTemplate).delete("pwd_reset:valid-token");
    }

    // ── getAllUsers (admin) ────────────────────────────────────────────

    @Test
    @DisplayName("getAllUsers() — throws when requester is not admin")
    void getAllUsers_notAdmin_throws() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        assertThatThrownBy(() -> sut.getAllUsers("user-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getAllUsers() — returns all users for admin")
    void getAllUsers_admin_success() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findAll()).thenReturn(List.of(activeUser, adminUser));
        var result = sut.getAllUsers("admin-1");
        assertThat(result).hasSize(2);
    }

    // ── toggleUserActive ──────────────────────────────────────────────

    @Test
    @DisplayName("toggleUserActive() — throws when admin tries to suspend self")
    void toggleUserActive_selfSuspend_throws() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        assertThatThrownBy(() -> sut.toggleUserActive("admin-1", "admin-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("own");
    }

    @Test
    @DisplayName("toggleUserActive() — suspends user and sets Redis revocation key")
    void toggleUserActive_suspends() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser), Optional.of(activeUser));

        sut.toggleUserActive("admin-1", "user-1");

        verify(userRepository).setIsActive("user-1", false);
        verify(valueOps).set(eq("suspended:user-1"), eq("1"), anyLong(), any());
    }

    // ── adminDeleteUser ───────────────────────────────────────────────

    @Test
    @DisplayName("adminDeleteUser() — throws when admin deletes self")
    void adminDeleteUser_selfDelete_throws() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        assertThatThrownBy(() -> sut.adminDeleteUser("admin-1", "admin-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("adminDeleteUser() — deletes target user")
    void adminDeleteUser_success() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));

        sut.adminDeleteUser("admin-1", "user-1");

        verify(userRepository).deleteByUserId("user-1");
    }

    // ── upgradePlan ───────────────────────────────────────────────────

    @Test
    @DisplayName("upgradePlan() — throws for invalid plan string")
    void upgradePlan_invalidPlan_throws() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        assertThatThrownBy(() -> sut.upgradePlan("user-1", "GOLD"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid plan");
    }

    @Test
    @DisplayName("upgradePlan() — upgrades to PREMIUM and publishes event")
    void upgradePlan_premium_publishesEvent() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenReturn(activeUser);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("new_jwt");
        when(jwtUtil.generateRefreshToken(any(), any())).thenReturn("new_refresh");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        sut.upgradePlan("user-1", "PREMIUM");

        assertThat(activeUser.getPlan()).isEqualTo(PlanType.PREMIUM);
        verify(eventPublisher).publishPlanUpgraded(any(), any(), any());
    }

    // ── submitReport ──────────────────────────────────────────────────

    @Test
    @DisplayName("submitReport() — throws when user reports self")
    void submitReport_selfReport_throws() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        User same = User.builder().userId("user-1").username("alice").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(same));

        SubmitReportRequest req = new SubmitReportRequest();
        req.setReportedUsername("alice");
        req.setReason("Spam");

        assertThatThrownBy(() -> sut.submitReport("user-1", req))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("yourself");
    }

    @Test
    @DisplayName("submitReport() — saves and returns report")
    void submitReport_success() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(activeUser));
        User bob = User.builder().userId("user-2").username("bob").build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        UserReport saved = UserReport.builder().reportId("r-1").reporterId("user-1")
                .reportedUserId("user-2").reason("Spam")
                .reporterUsername("alice").reportedUsername("bob")
                .status(UserReport.ReportStatus.PENDING).build();
        when(userReportRepository.save(any())).thenReturn(saved);

        SubmitReportRequest req = new SubmitReportRequest();
        req.setReportedUsername("bob");
        req.setReason("Spam");

        var resp = sut.submitReport("user-1", req);
        assertThat(resp.getReportId()).isEqualTo("r-1");
    }

    // ── updateReportStatus ────────────────────────────────────────────

    @Test
    @DisplayName("updateReportStatus() — throws for invalid action")
    void updateReportStatus_invalidAction_throws() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        UserReport report = UserReport.builder().reportId("r-1")
                .status(UserReport.ReportStatus.PENDING).build();
        when(userReportRepository.findById("r-1")).thenReturn(Optional.of(report));
        assertThatThrownBy(() -> sut.updateReportStatus("admin-1", "r-1", "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateReportStatus() — throws when trying to set back to PENDING")
    void updateReportStatus_toPending_throws() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        UserReport report = UserReport.builder().reportId("r-1")
                .status(UserReport.ReportStatus.PENDING).build();
        when(userReportRepository.findById("r-1")).thenReturn(Optional.of(report));
        assertThatThrownBy(() -> sut.updateReportStatus("admin-1", "r-1", "PENDING"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateReportStatus() — resolves report successfully")
    void updateReportStatus_resolved_success() {
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));
        UserReport report = UserReport.builder().reportId("r-1")
                .reporterId("user-1").reportedUserId("user-2")
                .reporterUsername("alice").reportedUsername("bob")
                .reason("Spam").status(UserReport.ReportStatus.PENDING).build();
        when(userReportRepository.findById("r-1")).thenReturn(Optional.of(report));
        when(userReportRepository.save(any())).thenReturn(report);

        sut.updateReportStatus("admin-1", "r-1", "RESOLVED");
        assertThat(report.getStatus()).isEqualTo(UserReport.ReportStatus.RESOLVED);
    }

    // ── helpers ───────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setEmail("alice@example.com");
        r.setUsername("alice");
        r.setFullName("Alice Smith");
        r.setPassword("Pass1@word");
        return r;
    }
}
