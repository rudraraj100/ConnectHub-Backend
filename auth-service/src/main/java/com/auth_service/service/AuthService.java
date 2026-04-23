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

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void logout(String token);

    AuthResponse refreshToken(String refreshToken);

    UserProfileResponse getUserById(String userId);

    UserProfileResponse updateProfile(String userId, UpdateProfileRequest request);

    void changePassword(String userId, ChangePasswordRequest request);

    List<UserProfileResponse> searchUsers(String keyword);

    UserProfileResponse updateStatus(String userId, UpdateStatusRequest request);

    boolean checkUsernameAvailability(String username);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
