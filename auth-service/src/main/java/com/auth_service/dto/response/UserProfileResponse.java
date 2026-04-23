package com.auth_service.dto.response;

import com.auth_service.entity.AuthProvider;
import com.auth_service.entity.UserStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {

    private String userId;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String bio;
    private UserStatus status;
    private AuthProvider provider;
    private boolean isActive;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private String country;
    private String city;
    private String countryCode;
    private String phoneNumber;
}
