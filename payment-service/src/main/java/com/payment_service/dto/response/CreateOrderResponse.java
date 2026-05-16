package com.payment_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned by POST /payments/orders.
 *
 * The frontend feeds razorpayOrderId, amount, currency, and keyId
 * directly into the Razorpay checkout options object:
 *
 *   var options = {
 *     key:      createOrderResponse.keyId,
 *     amount:   createOrderResponse.amount,
 *     currency: createOrderResponse.currency,
 *     order_id: createOrderResponse.razorpayOrderId,
 *     ...
 *   };
 *   var rzp = new Razorpay(options);
 *   rzp.open();
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderResponse {

    /** Our internal order ID (UUID in payment_orders table) */
    private String orderId;

    /** Razorpay order ID — must be passed to Razorpay checkout */
    private String razorpayOrderId;

    /** Amount in paise (e.g. 49900 = ₹499) */
    private int amount;

    /** Currency code (INR) */
    private String currency;

    /** Razorpay public Key Id — passed to the frontend checkout */
    private String keyId;

    /** Plan being purchased */
    private String plan;
}
