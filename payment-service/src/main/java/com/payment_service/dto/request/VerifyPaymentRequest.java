package com.payment_service.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /payments/verify.
 *
 * The three fields are returned by the Razorpay checkout JS popup after the
 * user completes payment. We HMAC-SHA256 verify them before upgrading the plan.
 *
 * Razorpay docs: https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/build-integration/#14-capture-payment-on-the-server-side
 */
public record VerifyPaymentRequest(

    /** Returned by Razorpay JS: response.razorpay_order_id */
    @NotBlank String razorpayOrderId,

    /** Returned by Razorpay JS: response.razorpay_payment_id */
    @NotBlank String razorpayPaymentId,

    /** Returned by Razorpay JS: response.razorpay_signature */
    @NotBlank String razorpaySignature
) {}
