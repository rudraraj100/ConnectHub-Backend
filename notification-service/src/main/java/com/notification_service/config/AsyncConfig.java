package com.notification_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // Enables @Async on sendEmail() and sendPushNotification()
    // so email/FCM calls never block the request thread
}
