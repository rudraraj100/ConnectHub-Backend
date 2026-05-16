package com.notification_service.service;

import com.notification_service.dto.NotificationDTO;
import com.notification_service.dto.SendNotificationRequest;

import java.util.List;

public interface NotifService {

    /** Persist + push in-app via WebSocket */
    NotificationDTO send(SendNotificationRequest request);

    /** Send to multiple recipients at once */
    void sendBulk(List<String> recipientIds, String type, String title, String message);

    /** FCM push notification (for offline users) */
    void sendPushNotification(String recipientId, String title, String body);

    /** Mark a single notification as read */
    void markAsRead(Long notificationId);

    /** Mark all notifications as read for a user */
    void markAllRead(String recipientId);

    /** Get all notifications for a user (newest first) */
    List<NotificationDTO> getByRecipient(String recipientId);

    /** Get all notifications (admin) */
    List<NotificationDTO> getAll();

    /** Unread count for the badge */
    long getUnreadCount(String recipientId);

    /** Delete a single notification */
    void deleteNotification(Long notificationId);

    /** Email fallback for missed DMs */
    void sendEmail(String to, String subject, String body);
}
