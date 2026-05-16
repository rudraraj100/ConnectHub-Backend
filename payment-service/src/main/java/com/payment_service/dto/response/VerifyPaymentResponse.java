package com.payment_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned by POST /payments/verify on successful signature check.
 *
 * newToken is a freshly issued JWT with plan=PREMIUM baked in.
 * The frontend should replace its stored jwt_token with this value
 * so premium features activate immediately — no re-login required.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VerifyPaymentResponse {

    private boolean verified;
    private String  plan;           // e.g. "PREMIUM"
    private String  newToken;       // fresh JWT with updated plan claim
    private String  refreshToken;   // fresh refresh token
    private String  message;
}
