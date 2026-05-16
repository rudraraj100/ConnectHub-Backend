package com.notification_service.dto;

import lombok.Data;
import java.util.List;

/**
 * Request body for POST /notifications/broadcast.
 * Sent by the platform admin dashboard to push a SYSTEM notification
 * to every user on the platform simultaneously.
 */
@Data
public class BroadcastRequest {

    /** All active user IDs to receive the notification. */
    private List<String> recipientIds;

    /** Short notification title, e.g. "Platform Maintenance Tonight". */
    private String title;

    /** Notification body text. */
    private String message;
}
