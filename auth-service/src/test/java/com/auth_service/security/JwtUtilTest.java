package com.auth_service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil - unit tests")
class JwtUtilTest {

    private JwtUtil sut;

    @BeforeEach
    void setUp() {
        sut = new JwtUtil();
        // Use a 32-character secret for HS256
        ReflectionTestUtils.setField(sut, "jwtSecret", "mySecretKeyForTestingConnectHub123!");
        ReflectionTestUtils.setField(sut, "jwtExpirationMs", 3600000L); // 1 hour
        ReflectionTestUtils.setField(sut, "refreshExpirationMs", 86400000L); // 24 hours
    }

    @Test
    @DisplayName("generateToken() creates a valid JWT with claims")
    void generateToken_createsValidJwt() {
        String token = sut.generateToken("user-1", "alice@example.com", "PREMIUM");

        assertThat(token).isNotNull();
        assertThat(sut.extractUserId(token)).isEqualTo("user-1");
        assertThat(sut.extractEmail(token)).isEqualTo("alice@example.com");
        assertThat(sut.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("generateRefreshToken() creates a valid refresh token")
    void generateRefreshToken_createsValidJwt() {
        String token = sut.generateRefreshToken("user-1", "alice@example.com");

        assertThat(token).isNotNull();
        assertThat(sut.extractUserId(token)).isEqualTo("user-1");
        assertThat(sut.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenExpired() returns true for expired token")
    void isTokenExpired_returnsTrueForExpired() {
        // Set expiration to 1ms
        ReflectionTestUtils.setField(sut, "jwtExpirationMs", 1L);
        String token = sut.generateToken("u1", "e1");

        // Wait a bit
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(sut.isTokenExpired(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken() returns false for malformed token")
    void validateToken_returnsFalseForMalformed() {
        assertThat(sut.validateToken("not.a.token")).isFalse();
    }
}
