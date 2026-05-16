package com.payment_service.entity;

/**
 * PaymentStatus — lifecycle of a Razorpay payment order.
 *
 *  CREATED → order was created; awaiting user checkout
 *  PAID    → payment verified via HMAC; plan upgraded in auth-service
 *  FAILED  → signature mismatch or user cancelled
 */
public enum PaymentStatus {
    CREATED,
    PAID,
    FAILED
}
