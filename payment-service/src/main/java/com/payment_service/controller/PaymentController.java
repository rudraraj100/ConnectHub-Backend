package com.payment_service.controller;

import com.payment_service.dto.request.VerifyPaymentRequest;
import com.payment_service.dto.response.ApiResponse;
import com.payment_service.dto.response.CreateOrderResponse;
import com.payment_service.dto.response.VerifyPaymentResponse;
import com.payment_service.entity.PaymentOrder;
import com.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PaymentController handles the checkout flow for premium subscriptions.
 * It integrates with Razorpay to create orders and verify payments.
 */
@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Razorpay payment and premium plan upgrade")
public class PaymentController {

    private final PaymentService paymentService;

    // ── Create Order ─────────────────────────────────────────────────────────

    /**
     * Creates a new Razorpay order. 
     * The frontend uses this order ID to launch the Razorpay payment window.
     */
    @PostMapping("/orders")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a Razorpay order for PREMIUM subscription",
               description = "Returns an `orderId` used to open the Razorpay payment modal on the frontend.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Razorpay order created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Razorpay API error", content = @Content)
    })
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @RequestHeader("X-User-Id") String userId) {

        log.debug("[Payment] createOrder requested by user {}", userId);
        return ResponseEntity.ok(ApiResponse.ok(
                "Order created", paymentService.createOrder(userId)));
    }

    // ── Verify Payment ───────────────────────────────────────────────────────

    /**
     * Verifies the payment signature returned by Razorpay.
     * If successful, it upgrades the user's plan to PREMIUM and returns a new JWT token.
     */
    @PostMapping("/verify")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Verify Razorpay payment signature and upgrade plan to PREMIUM",
               description = """
                       Verifies the HMAC-SHA256 signature returned by Razorpay. On success:
                       1. Payment record is saved
                       2. auth-service is called to upgrade user plan
                       3. A fresh JWT with `plan=PREMIUM` is returned
                       """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment verified, plan upgraded, new JWT returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Signature verification failed", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<VerifyPaymentResponse>> verifyPayment(
            @RequestHeader("X-User-Id")         String userId,
            @Valid @RequestBody VerifyPaymentRequest request) {

        log.debug("[Payment] verifyPayment for user {} order {}", userId, request.razorpayOrderId());
        return ResponseEntity.ok(ApiResponse.ok(
                "Payment verified", paymentService.verifyPayment(userId, request)));
    }

    // ── Payment History ──────────────────────────────────────────────────────

    /**
     * GET /payments/history
     *
     * Returns the authenticated user's payment records (newest first).
     */
    @GetMapping("/history")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the current user's payment history",
               description = "Returns all payment records for the authenticated user, newest first.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment history returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT missing or invalid", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<PaymentOrder>>> getHistory(
            @RequestHeader("X-User-Id") String userId) {

        return ResponseEntity.ok(ApiResponse.ok(
                "Payment history", paymentService.getPaymentHistory(userId)));
    }
}
