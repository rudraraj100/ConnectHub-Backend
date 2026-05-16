package com.messageservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Feign client for auth-service.
 * Used to enrich MessageResponse with senderName and senderAvatar.
 *
 * Pattern: same as room-service AuthServiceClient.
 * The gateway strips and re-injects X-User-Id, but for service-to-service
 * calls we pass the Authorization header through FeignConfig.
 */
@FeignClient(name = "auth-service", url = "${auth.service.url:http://localhost:8081}")
public interface AuthServiceClient {

    /**
     * GET /auth/users/{userId}
     * Returns { success, message, data: { userId, username, fullName, avatarUrl, ... } }
     */
    @GetMapping("/auth/users/{userId}")
    Map<String, Object> getUserById(@PathVariable("userId") String userId);
}
