package com.auth_service.dto.response;

import com.auth_service.entity.AuthProvider;
import com.auth_service.entity.PlanType;
import com.auth_service.entity.PlatformRole;
import com.auth_service.entity.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isActive")
    private boolean isActive;
    private PlatformRole role;
    /** Subscription plan — FREE (default) or PREMIUM. */
    private PlanType plan;
    /** Custom presence text set by premium users (null for free users). */
    private String customStatus;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private String country;
    private String city;
    private String countryCode;
    private String phoneNumber;
}
