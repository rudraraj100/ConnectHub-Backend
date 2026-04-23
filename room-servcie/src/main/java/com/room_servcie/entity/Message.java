package com.room_servcie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Message entity — stored in MySQL for now.
 * Will be migrated to MongoDB when Message Service is implemented.
 * §2.4: Infinite scroll via paginated REST (sorted by createdAt DESC).
 */
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_msg_room",    columnList = "room_id"),
    @Index(name = "idx_msg_sender",  columnList = "sender_id"),
    @Index(name = "idx_msg_created", columnList = "created_at"),
    @Index(name = "idx_msg_pinned",  columnList = "is_pinned")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @Column(name = "message_id", updatable = false, nullable = false, length = 36)
    private String messageId;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(name = "sender_id", nullable = false, length = 36)
    private String senderId;

    /** Message text content */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** TEXT | IMAGE | FILE | SYSTEM */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String type = "TEXT";

    /** URL for image/file messages */
    @Column(name = "file_url", length = 512)
    private String fileUrl;

    /** §2.5: Room Admin can pin a message */
    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private boolean isPinned = false;

    /** Soft delete — message content hidden but record kept */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /** Message being replied to (threading) */
    @Column(name = "reply_to_id", length = 36)
    private String replyToId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.messageId == null) this.messageId = UUID.randomUUID().toString();
    }
}
