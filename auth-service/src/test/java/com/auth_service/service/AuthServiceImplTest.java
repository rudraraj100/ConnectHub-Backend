package com.auth_service.service;

import com.auth_service.dto.request.*;
import com.auth_service.dto.response.*;
import com.auth_service.entity.*;
import com.auth_service.messaging.AuthEventPublisher;
import com.auth_service.repository.UserReportRepository;
import com.auth_service.repository.UserRepository;
import com.auth_service.security.JwtUtil;
import com.auth_service.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl - unit tests")
class AuthServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock UserReportRepository userReportRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AuthEventPublisher eventPublisher;
    @Mock AuthenticationManager authenticationManager;
    @Mock JavaMailSender mailSender;

    @InjectMocks AuthServiceImpl sut;

    private User verifiedUser;
    private User unverifiedUser;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        verifiedUser = User.builder()
                .userId("user-1")
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashed-password")
                .isActive(true)
                .emailVerified(true)
                .status(UserStatus.ONLINE)
                .plan(PlanType.FREE)
                .build();

        unverifiedUser = User.builder()
                .userId("user-2")
                .username("bob")
                .email("bob@example.com")
                .passwordHash("hashed-password")
                .isActive(false)
                .emailVerified(false)
                .status(UserStatus.ONLINE)
                .plan(PlanType.FREE)
                .build();
    }

    @Test
    @DisplayName("register() succeeds for a new user")
    void register_success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@example.com");
        req.setUsername("newuser");
        req.setPassword("pass123");
        req.setFullName("New User");

        String result = sut.register(req);
        assertThat(result).contains("OTP sent");
        verify(userRepository).save(any());
        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("login() succeeds for verified user")
    void login_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(verifiedUser));
        when(jwtUtil.generateToken(anyString(), anyString(), anyString())).thenReturn("mock-token");
        when(jwtUtil.generateRefreshToken(anyString(), anyString())).thenReturn("mock-refresh");

        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("pass123");

        AuthResponse resp = sut.login(req);
        assertThat(resp.getToken()).isEqualTo("mock-token");
        assertThat(resp.getUser().getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("verifyEmail() activates user on correct OTP")
    void verifyEmail_success() {
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(valueOps.get("email_verify_otp:bob@example.com")).thenReturn("123456");

        sut.verifyEmail("bob@example.com", "123456");

        assertThat(unverifiedUser.isEmailVerified()).isTrue();
        assertThat(unverifiedUser.isActive()).isTrue();
        verify(eventPublisher).publishUserVerified(any(), any(), any());
    }

    @Test
    @DisplayName("logout() blacklists token in Redis")
    void logout_success() {
        when(jwtUtil.extractExpiration(anyString())).thenReturn(new Date(System.currentTimeMillis() + 10000));

        sut.logout("my-token");

        verify(valueOps).set(eq("blacklist:my-token"), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }
}
