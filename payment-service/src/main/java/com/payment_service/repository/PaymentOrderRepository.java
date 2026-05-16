package com.payment_service.repository;

import com.payment_service.entity.PaymentOrder;
import com.payment_service.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByUserIdAndStatus(String userId, PaymentStatus status);
}
