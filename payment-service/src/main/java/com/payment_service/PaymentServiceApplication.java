package com.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * PaymentServiceApplication — ConnectHub payment microservice.
 *
 * Responsibilities:
 *  1. Create Razorpay orders (POST /payments/orders)
 *  2. Verify payment signatures (POST /payments/verify)
 *  3. After verification, call auth-service to upgrade user.plan → PREMIUM
 *  4. Return a fresh JWT so the frontend can use premium features immediately
 *
 * Security model (matches all other ConnectHub services):
 *  - No Spring Security / JWT parsing here
 *  - The API Gateway's JwtGatewayFilter validates the token and injects:
 *      X-User-Id   → the authenticated user's UUID
 *      X-User-Plan → their current plan (FREE | PREMIUM)
 *  - Controllers read X-User-Id directly from headers
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
