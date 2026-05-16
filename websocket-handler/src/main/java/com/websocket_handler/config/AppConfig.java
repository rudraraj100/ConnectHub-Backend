package com.websocket_handler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * AppConfig — provides shared beans.
 *
 * RestTemplate is used to call downstream services:
 *   message-service     :8083
 *   presence-service    :8085
 *   notification-service:8086
 *   room-service        :8082
 */
@Configuration
@EnableAsync
public class AppConfig {

    @Value("${app.services.message-service}")
    public String messageServiceUrl;

    @Value("${app.services.presence-service}")
    public String presenceServiceUrl;

    @Value("${app.services.notification-service}")
    public String notificationServiceUrl;

    @Value("${app.services.room-service}")
    public String roomServiceUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
