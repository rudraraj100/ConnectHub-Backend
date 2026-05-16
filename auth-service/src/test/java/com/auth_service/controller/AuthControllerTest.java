package com.auth_service.controller;

import com.auth_service.dto.request.LoginRequest;
import com.auth_service.dto.request.RegisterRequest;
import com.auth_service.dto.response.ApiResponse;
import com.auth_service.dto.response.AuthResponse;
import com.auth_service.dto.response.UserProfileResponse;
import com.auth_service.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController - unit tests")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController sut;

    @Test
    @DisplayName("register() returns 202 Accepted")
    void register_returnsAccepted() {
        RegisterRequest req = new RegisterRequest();
        when(authService.register(any())).thenReturn("OTP sent");

        ResponseEntity<ApiResponse<String>> resp = sut.register(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody().getMessage()).isEqualTo("OTP sent");
    }

    @Test
    @DisplayName("login() returns 200 OK with AuthResponse")
    void login_returnsOk() {
        LoginRequest req = new LoginRequest();
        AuthResponse auth = AuthResponse.builder().token("tk").build();
        when(authService.login(any())).thenReturn(auth);

        ResponseEntity<ApiResponse<AuthResponse>> resp = sut.login(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getToken()).isEqualTo("tk");
    }

    @Test
    @DisplayName("getProfile() calls service with userId header")
    void getProfile_callsService() {
        UserProfileResponse profile = UserProfileResponse.builder().userId("u1").build();
        when(authService.getUserById("u1")).thenReturn(profile);

        ResponseEntity<ApiResponse<UserProfileResponse>> resp = sut.getProfile("u1");

        assertThat(resp.getBody().getData().getUserId()).isEqualTo("u1");
        verify(authService).getUserById("u1");
    }

    @Test
    @DisplayName("logout() blacklists token")
    void logout_blacklistsToken() {
        ResponseEntity<ApiResponse<Void>> resp = sut.logout("Bearer my-token");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authService).logout("my-token");
    }

    @Test
    @DisplayName("checkUsername() returns availability")
    void checkUsername_returnsValue() {
        when(authService.checkUsernameAvailability("alice")).thenReturn(true);

        ResponseEntity<ApiResponse<Boolean>> resp = sut.checkUsername("alice");

        assertThat(resp.getBody().getData()).isTrue();
        verify(authService).checkUsernameAvailability("alice");
    }
}
