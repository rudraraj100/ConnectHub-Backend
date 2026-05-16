package com.notification_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Notification entity — persisted in MySQL.
 *
 * Types: NEW_MESSAGE | MENTION | ROOM_INVITE | SYSTEM
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_recipient_id",  columnList = "recipientId"),
        @Index(name = "idx_recipient_read", columnList = "recipientId, isRead")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Column(nullable = false)
    private String recipientId;   // who receives it

    private String actorId;       // who triggered it (null for SYSTEM)

    @Column(nullable = false)
    private String type;          // NEW_MESSAGE | MENTION | ROOM_INVITE | SYSTEM

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String message;

    private String roomId;
    private String messageId;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
