package com.room_servcie.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "room_members", uniqueConstraints = {
    @UniqueConstraint(name = "uk_room_user", columnNames = {"room_id", "user_id"})
}, indexes = {
    @Index(name = "idx_rm_user",        columnList = "user_id"),
    @Index(name = "idx_rm_room",        columnList = "room_id"),
    @Index(name = "idx_rm_last_read",   columnList = "last_read_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomMember {

    @Id
    @Column(name = "member_id", updatable = false, nullable = false, length = 36)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** userId from auth-service */
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private RoomMemberRole role = RoomMemberRole.MEMBER;

    /**
     * Muted = can read messages but cannot send.
     * §2.5: Room Admin can mute a member.
     */
    @Column(name = "is_muted", nullable = false)
    @Builder.Default
    private boolean isMuted = false;

    /**
     * Used for unread count: messages after this timestamp are unread.
     * §2.4: Unread count tracked per user per room using lastReadAt.
     */
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        if (this.memberId == null) this.memberId = UUID.randomUUID().toString();
        if (this.joinedAt == null) this.joinedAt = LocalDateTime.now();
    }
}
