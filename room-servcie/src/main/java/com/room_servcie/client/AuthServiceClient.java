package com.room_servcie.client;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * HTTP client to call auth-service for user profile lookups.
 * Uses RestTemplate directly (avoids Feign startup issues without Eureka).
 * §2.4: Used to enrich RoomMemberResponse with username, avatar, status.
 */
@Component
public class AuthServiceClient {

    @Value("${auth.service.url}")
    private String authServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public UserProfileDto getUserById(String userId, String jwtToken) {
        try {
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            var entity = new org.springframework.http.HttpEntity<>(headers);

            var response = restTemplate.exchange(
                authServiceUrl + "/auth/users/" + userId,
                org.springframework.http.HttpMethod.GET,
                entity,
                ApiWrapper.class
            );
            if (response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData();
            }
        } catch (Exception e) {
            // Auth service unavailable — return a placeholder
        }
        return new UserProfileDto(userId, "user_" + userId.substring(0, 6), "", "", "OFFLINE");
    }

    // ── Inner DTOs ────────────────────────────────────────────────
    @Data
    public static class ApiWrapper {
        private boolean success;
        private UserProfileDto data;
    }

    @Data
    public static class UserProfileDto {
        private String userId;
        private String username;
        private String fullName;
        private String avatarUrl;
        private String status;
        private boolean isActive;
        private LocalDateTime createdAt;

        public UserProfileDto() {}
        public UserProfileDto(String userId, String username, String fullName,
                              String avatarUrl, String status) {
            this.userId    = userId;
            this.username  = username;
            this.fullName  = fullName;
            this.avatarUrl = avatarUrl;
            this.status    = status;
        }
    }
}
