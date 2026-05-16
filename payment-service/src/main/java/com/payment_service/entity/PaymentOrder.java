package com.payment_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PaymentOrder — persists every Razorpay order and its final status.
 *
 * Lifecycle:
 *  CREATED → user was shown the Razorpay checkout popup
 *  PAID    → payment verified via HMAC signature; plan upgraded in auth-service
 *  FAILED  → signature mismatch or Razorpay returned a failure
 */
@Entity
@Table(name = "payment_orders", indexes = {
    @Index(name = "idx_po_user",        columnList = "user_id"),
    @Index(name = "idx_po_rzp_order",   columnList = "razorpay_order_id",  unique = true),
    @Index(name = "idx_po_rzp_payment", columnList = "razorpay_payment_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentOrder {

    @Id
    @Column(name = "order_id", updatable = false, nullable = false, length = 36)
    private String orderId;

    /** ConnectHub userId (from X-User-Id header) */
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Razorpay order ID returned when we create the order */
    @Column(name = "razorpay_order_id", nullable = false, length = 100, unique = true)
    private String razorpayOrderId;

    /** Razorpay payment ID — set after successful payment verification */
    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    /** Amount in smallest currency unit (paise for INR). e.g. ₹499 = 49900 */
    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "INR";

    /** Plan purchased — always PREMIUM for now */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String plan = "PREMIUM";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    public void prePersist() {
        if (this.orderId == null) this.orderId = UUID.randomUUID().toString();
    }
}
