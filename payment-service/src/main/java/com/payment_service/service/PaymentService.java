package com.payment_service.service;

import com.payment_service.dto.request.VerifyPaymentRequest;
import com.payment_service.dto.response.CreateOrderResponse;
import com.payment_service.dto.response.VerifyPaymentResponse;
import com.payment_service.entity.PaymentOrder;

import java.util.List;

public interface PaymentService {

    /**
     * Creates a Razorpay order for the PREMIUM plan and persists a local
     * PaymentOrder record with status=CREATED.
     *
     * @param userId taken from X-User-Id header (injected by the gateway)
     * @return order details the frontend passes to the Razorpay checkout popup
     */
    CreateOrderResponse createOrder(String userId);

    /**
     * Verifies the HMAC-SHA256 signature from the Razorpay JS callback.
     * On success:
     *  1. Updates the local PaymentOrder (status=PAID, paidAt=now)
     *  2. Calls auth-service to set user.plan = PREMIUM
     *  3. Returns a fresh JWT with plan=PREMIUM so the frontend can use premium
     *     features immediately without re-login.
     *
     * On failure throws {@link org.springframework.web.server.ResponseStatusException}
     * with 400 BAD_REQUEST.
     *
     * @param userId  from X-User-Id header
     * @param request the three Razorpay identifiers from the JS popup
     */
    VerifyPaymentResponse verifyPayment(String userId, VerifyPaymentRequest request);

    /**
     * Returns the payment history for a user (newest first).
     */
    List<PaymentOrder> getPaymentHistory(String userId);
}
