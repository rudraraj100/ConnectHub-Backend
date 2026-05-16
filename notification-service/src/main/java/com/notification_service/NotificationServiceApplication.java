package com.notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;

/**
 * Notification Service — real-time push notifications via STOMP WebSocket.
 *
 * Security is handled by the API Gateway. This service must NOT run Spring Security.
 *
 * WHY: spring-boot-starter-websocket pulls in spring-security transitively.
 * When Spring Security is active, it intercepts the WebSocket upgrade request
 * (/ws-notifications) and applies CSRF protection. Since the browser WebSocket API
 * cannot send CSRF tokens, the handshake is rejected with HTTP 400 — silently
 * killing all real-time notification pushes.
 *
 * All three security auto-configs are excluded:
 *   SecurityAutoConfiguration              — disables form-login & security filters
 *   UserDetailsServiceAutoConfiguration    — prevents in-memory user bean
 *   ManagementWebSecurityAutoConfiguration — prevents Actuator security filter chain
 *                                            (which requires HttpSecurity and crashes
 *                                             when SecurityAutoConfiguration is excluded)
 */
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        ManagementWebSecurityAutoConfiguration.class
})
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

}
