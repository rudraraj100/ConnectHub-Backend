package com.payment_service.service.impl;

import com.payment_service.client.AuthServiceClient;
import com.payment_service.dto.request.VerifyPaymentRequest;
import com.payment_service.dto.response.CreateOrderResponse;
import com.payment_service.dto.response.VerifyPaymentResponse;
import com.payment_service.entity.PaymentOrder;
import com.payment_service.entity.PaymentStatus;
import com.payment_service.repository.PaymentOrderRepository;
import com.payment_service.service.PaymentService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * PaymentServiceImpl — Razorpay order creation and HMAC signature verification.
 *
 * Flow:
 *  1. createOrder()   → Razorpay API → persist PaymentOrder(CREATED) → return to frontend
 *  2. verifyPayment() → HMAC check   → update PaymentOrder(PAID)     → call auth-service
 *                     → return fresh JWT with plan=PREMIUM
 *
 * Security model:
 *  - No Spring Security here (same as room/message/notification-service).
 *  - userId is read from X-User-Id header (injected by the API Gateway).
 *  - Payment verification uses Razorpay's HMAC-SHA256 signature — forgery impossible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final RazorpayClient         razorpayClient;
    private final PaymentOrderRepository paymentOrderRepository;
    private final AuthServiceClient      authServiceClient;

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    @Value("${razorpay.premium-amount:49900}")
    private int premiumAmount;          // paise — 49900 = ₹499

    // ── Create Order ─────────────────────────────────────────────────────────

    @Override
    public CreateOrderResponse createOrder(String userId) {
        try {
            // Receipt must be ≤ 40 chars (Razorpay hard limit).
            // Format: rcpt_{8-char-userId}_{7-digit-epoch}  = 21 chars
            String shortUserId = userId != null && userId.length() >= 8
                    ? userId.replace("-", "").substring(0, 8)
                    : userId;
            String receipt = "rcpt_" + shortUserId + "_" + (System.currentTimeMillis() % 10_000_000L);

            // Build Razorpay order request
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount",   premiumAmount);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt",  receipt);  // ≤ 40 chars enforced above
            orderRequest.put("notes",    new JSONObject()
                    .put("userId", userId)
                    .put("plan",   "PREMIUM"));

            log.info("[Payment] Creating Razorpay order — userId={} receipt={} amount={}", userId, receipt, premiumAmount);
            Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = razorpayOrder.get("id");

            log.info("[Payment] Created Razorpay order {} for user {}", razorpayOrderId, userId);

            // Persist a local record
            PaymentOrder local = PaymentOrder.builder()
                    .userId(userId)
                    .razorpayOrderId(razorpayOrderId)
                    .amount(premiumAmount)
                    .currency(currency)
                    .plan("PREMIUM")
                    .status(PaymentStatus.CREATED)
                    .build();
            paymentOrderRepository.save(local);

            return CreateOrderResponse.builder()
                    .orderId(local.getOrderId())
                    .razorpayOrderId(razorpayOrderId)
                    .amount(premiumAmount)
                    .currency(currency)
                    .keyId(keyId)
                    .plan("PREMIUM")
                    .build();

        } catch (Exception e) {
            log.error("[Payment] createOrder FAILED for userId={} | error={} | type={}",
                    userId, e.getMessage(), e.getClass().getSimpleName(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not create payment order: " + e.getMessage());
        }
    }

    // ── Verify Payment ───────────────────────────────────────────────────────

    @Override
    public VerifyPaymentResponse verifyPayment(String userId, VerifyPaymentRequest req) {

        // ── Step 1: HMAC-SHA256 signature verification ──────────────────────
        // Razorpay generates: HMAC_SHA256(keySecret, orderId + "|" + paymentId)
        // We compute the same and compare — if they match, the payment is genuine.
        String expectedSignature = hmacSha256(
                req.razorpayOrderId() + "|" + req.razorpayPaymentId(),
                keySecret
        );

        if (!expectedSignature.equals(req.razorpaySignature())) {
            log.warn("[Payment] Signature mismatch for user {} order {}", userId, req.razorpayOrderId());

            // Mark the stored order as failed
            paymentOrderRepository.findByRazorpayOrderId(req.razorpayOrderId()).ifPresent(order -> {
                order.setStatus(PaymentStatus.FAILED);
                paymentOrderRepository.save(order);
            });

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment signature verification failed. Please contact support.");
        }

        // ── Step 2: Update local record ──────────────────────────────────────
        PaymentOrder order = paymentOrderRepository
                .findByRazorpayOrderId(req.razorpayOrderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment order not found: " + req.razorpayOrderId()));

        order.setRazorpayPaymentId(req.razorpayPaymentId());
        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);
        log.info("[Payment] Verified payment {} for user {}", req.razorpayPaymentId(), userId);

        // ── Step 3: Upgrade plan in auth-service ─────────────────────────────
        String newToken    = null;
        String refreshToken = null;
        try {
            Map<String, Object> authRes = authServiceClient.upgradePlan(userId, "PREMIUM");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) authRes.get("data");
            if (data != null) {
                newToken    = (String) data.get("token");
                refreshToken = (String) data.get("refreshToken");
            }
            log.info("[Payment] Plan upgraded to PREMIUM for user {}", userId);
        } catch (Exception e) {
            // Non-fatal: plan upgrade may retry; payment itself is already verified.
            log.error("[Payment] Failed to call auth-service for plan upgrade: {}", e.getMessage(), e);
        }

        return VerifyPaymentResponse.builder()
                .verified(true)
                .plan("PREMIUM")
                .newToken(newToken)
                .refreshToken(refreshToken)
                .message("Payment verified. Welcome to ConnectHub Premium! 🎉")
                .build();
    }

    // ── History ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PaymentOrder> getPaymentHistory(String userId) {
        return paymentOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ── HMAC Helper ──────────────────────────────────────────────────────────

    /**
     * Computes HMAC-SHA256(secret, data) and returns the lowercase hex string.
     * Razorpay uses this to sign the orderId|paymentId pair.
     */
    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }
}
