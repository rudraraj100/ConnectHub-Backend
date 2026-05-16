package com.payment_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign client for calling auth-service's internal plan-upgrade endpoint.
 *
 * Uses the direct URL (not the gateway) because:
 *  a) Payment-service is already inside the trusted network
 *  b) The gateway would require a valid JWT on all /auth/** requests
 *  c) At the point of plan upgrade there's no user JWT to forward
 *
 * Pattern: identical to message-service's AuthServiceClient.
 */
@FeignClient(name = "auth-service", url = "${auth.service.url:http://localhost:8081}")
public interface AuthServiceClient {

    /**
     * PUT /auth/internal/upgrade-plan/{userId}?plan=PREMIUM
     *
     * Returns { success, message, data: { token, refreshToken, expiresIn, user } }
     * The token has plan=PREMIUM in its claims — frontend swaps it immediately.
     */
    @PutMapping("/auth/internal/upgrade-plan/{userId}")
    Map<String, Object> upgradePlan(
            @PathVariable("userId") String userId,
            @RequestParam("plan")   String plan);
}
