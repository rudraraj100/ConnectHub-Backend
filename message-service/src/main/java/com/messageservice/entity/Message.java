package com.messageservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Message entity — owns all chat message data.
 *
 * Case study §4.3:
 *   messageId (UUID), roomId, senderId, content, type (TEXT/IMAGE/FILE/REACTION/SYSTEM),
 *   mediaUrl, replyToMessageId, isEdited, isDeleted,
 *   deliveryStatus (SENT/DELIVERED/READ), sentAt, editedAt
 */
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_msg_room",    columnList = "room_id"),
    @Index(name = "idx_msg_sender",  columnList = "sender_id"),
    @Index(name = "idx_msg_sent_at", columnList = "sent_at"),
    @Index(name = "idx_msg_status",  columnList = "delivery_status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @Column(name = "message_id", updatable = false, nullable = false, length = 36)
    private String messageId;

    /** Room this message belongs to */
    @Column(name = "room_id", nullable = false, length = 255)
    private String roomId;

    /** userId of the sender (injected from X-User-Id gateway header) */
    @Column(name = "sender_id", nullable = false, length = 36)
    private String senderId;

    /** Main text body */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * TEXT | IMAGE | FILE | REACTION | SYSTEM
     * Case study §4.3 type field
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String type = "TEXT";

    /** S3 URL for IMAGE or FILE messages */
    @Column(name = "media_url", length = 512)
    private String mediaUrl;

    /** messageId of the message being replied to (threading) */
    @Column(name = "reply_to_message_id", length = 36)
    private String replyToMessageId;

    /** True when content has been edited; UI shows "edited" label */
    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private boolean isEdited = false;

    /** Soft delete — content hidden but record kept for thread integrity */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /**
     * SENT → DELIVERED → READ
     * Case study §4.3, §2.6 delivery status lifecycle
     */
    @Column(name = "delivery_status", nullable = false, length = 20)
    @Builder.Default
    private String deliveryStatus = "SENT";

    /** Server-side timestamp — used for ordering (case study §6 NFR) */
    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    /** Set when the message is edited */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @PrePersist
    public void prePersist() {
        if (this.messageId == null) this.messageId = UUID.randomUUID().toString();
    }
}
