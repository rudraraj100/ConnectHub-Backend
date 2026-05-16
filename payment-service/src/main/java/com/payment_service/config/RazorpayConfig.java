package com.payment_service.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RazorpayConfig — creates a single shared RazorpayClient bean.
 *
 * The client is thread-safe and expensive to construct (HTTP connection pool),
 * so it is a singleton managed by Spring rather than created per-request.
 */
@Slf4j
@Configuration
public class RazorpayConfig {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        log.info("[Razorpay] Initialising client with key-id: {}***", keyId.substring(0, Math.min(10, keyId.length())));
        return new RazorpayClient(keyId, keySecret);
    }
}
