package com.payment_service.service.impl;

import com.payment_service.client.AuthServiceClient;
import com.payment_service.dto.request.VerifyPaymentRequest;
import com.payment_service.dto.response.VerifyPaymentResponse;
import com.payment_service.entity.PaymentOrder;
import com.payment_service.entity.PaymentStatus;
import com.payment_service.repository.PaymentOrderRepository;
import com.razorpay.RazorpayClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl — unit tests")
class PaymentServiceImplTest {

    @Mock RazorpayClient         razorpayClient;
    @Mock PaymentOrderRepository paymentOrderRepository;
    @Mock AuthServiceClient      authServiceClient;

    @InjectMocks PaymentServiceImpl sut;

    private static final String KEY_SECRET = "test_secret_key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "keyId",         "test_key_id");
        ReflectionTestUtils.setField(sut, "keySecret",     KEY_SECRET);
        ReflectionTestUtils.setField(sut, "currency",      "INR");
        ReflectionTestUtils.setField(sut, "premiumAmount", 49900);
    }

    // ── getPaymentHistory ─────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentHistory() — returns list from repository")
    void getPaymentHistory_returnsList() {
        PaymentOrder order = PaymentOrder.builder()
                .orderId("ord-1").userId("user-1")
                .status(PaymentStatus.PAID).build();
        when(paymentOrderRepository.findByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(order));

        List<PaymentOrder> result = sut.getPaymentHistory("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("getPaymentHistory() — returns empty list when no orders")
    void getPaymentHistory_emptyList() {
        when(paymentOrderRepository.findByUserIdOrderByCreatedAtDesc("user-2"))
                .thenReturn(Collections.emptyList());

        List<PaymentOrder> result = sut.getPaymentHistory("user-2");

        assertThat(result).isEmpty();
    }

    // ── verifyPayment — signature mismatch ────────────────────────────

    @Test
    @DisplayName("verifyPayment() — throws 400 and marks FAILED on signature mismatch")
    void verifyPayment_signatureMismatch_throws400() {
        PaymentOrder order = PaymentOrder.builder()
                .orderId("ord-1").razorpayOrderId("rp_order_1")
                .userId("user-1").status(PaymentStatus.CREATED).build();
        when(paymentOrderRepository.findByRazorpayOrderId("rp_order_1"))
                .thenReturn(Optional.of(order));

        VerifyPaymentRequest req = new VerifyPaymentRequest(
                "rp_order_1", "pay_123", "WRONG_SIGNATURE");

        assertThatThrownBy(() -> sut.verifyPayment("user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("signature");

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentOrderRepository).save(order);
    }

    @Test
    @DisplayName("verifyPayment() — signature mismatch with missing order does not throw NPE")
    void verifyPayment_signatureMismatch_noOrder_throws400() {
        when(paymentOrderRepository.findByRazorpayOrderId("rp_order_99"))
                .thenReturn(Optional.empty());

        VerifyPaymentRequest req = new VerifyPaymentRequest(
                "rp_order_99", "pay_999", "WRONG_SIG");

        assertThatThrownBy(() -> sut.verifyPayment("user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("signature");
    }

    // ── verifyPayment — order not found ───────────────────────────────

    @Test
    @DisplayName("verifyPayment() — throws 404 when order not found after signature passes")
    void verifyPayment_orderNotFound_throws404() {
        String orderId    = "rp_order_2";
        String paymentId  = "pay_456";
        String validSig   = computeHmac(orderId + "|" + paymentId, KEY_SECRET);

        // First call (findByRazorpayOrderId for FAILED update) — mismatch won't happen (sig is valid)
        when(paymentOrderRepository.findByRazorpayOrderId(orderId))
                .thenReturn(Optional.empty());

        VerifyPaymentRequest req = new VerifyPaymentRequest(orderId, paymentId, validSig);

        assertThatThrownBy(() -> sut.verifyPayment("user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ── verifyPayment — success ───────────────────────────────────────

    @Test
    @DisplayName("verifyPayment() — marks PAID and returns verified=true")
    void verifyPayment_success() {
        String orderId   = "rp_order_3";
        String paymentId = "pay_789";
        String validSig  = computeHmac(orderId + "|" + paymentId, KEY_SECRET);

        PaymentOrder order = PaymentOrder.builder()
                .orderId("local-1").razorpayOrderId(orderId)
                .userId("user-1").status(PaymentStatus.CREATED).build();

        when(paymentOrderRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any())).thenReturn(order);

        Map<String, Object> authData = new HashMap<>();
        authData.put("data", Map.of("token", "new_jwt", "refreshToken", "new_refresh"));
        when(authServiceClient.upgradePlan("user-1", "PREMIUM")).thenReturn(authData);

        VerifyPaymentRequest req = new VerifyPaymentRequest(orderId, paymentId, validSig);
        VerifyPaymentResponse resp = sut.verifyPayment("user-1", req);

        assertThat(resp.isVerified()).isTrue();
        assertThat(resp.getPlan()).isEqualTo("PREMIUM");
        assertThat(resp.getNewToken()).isEqualTo("new_jwt");
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getRazorpayPaymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("verifyPayment() — still returns verified=true even if auth-service upgrade fails")
    void verifyPayment_authUpgradeFails_stillVerified() {
        String orderId   = "rp_order_4";
        String paymentId = "pay_101";
        String validSig  = computeHmac(orderId + "|" + paymentId, KEY_SECRET);

        PaymentOrder order = PaymentOrder.builder()
                .orderId("local-2").razorpayOrderId(orderId)
                .userId("user-1").status(PaymentStatus.CREATED).build();

        when(paymentOrderRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any())).thenReturn(order);
        when(authServiceClient.upgradePlan(any(), any())).thenThrow(new RuntimeException("auth down"));

        VerifyPaymentRequest req = new VerifyPaymentRequest(orderId, paymentId, validSig);
        VerifyPaymentResponse resp = sut.verifyPayment("user-1", req);

        assertThat(resp.isVerified()).isTrue();
        assertThat(resp.getNewToken()).isNull();
    }

    // ── createOrder — exception path ──────────────────────────────────

    @Test
    @DisplayName("createOrder() — throws 500 when Razorpay client fails")
    void createOrder_razorpayFails_throws500() {
        // razorpayClient.orders is null on a Mockito mock (no real constructor called),
        // so calling razorpayClient.orders.create() throws NullPointerException,
        // which the service catches and wraps as a 500 ResponseStatusException.
        assertThatThrownBy(() -> sut.createOrder("user-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Could not create payment order");
    }

    // ── helper: compute HMAC-SHA256 (mirrors PaymentServiceImpl logic) ──

    private String computeHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
