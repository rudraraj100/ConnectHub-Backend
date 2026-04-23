package com.room_servcie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms", indexes = {
    @Index(name = "idx_room_created_by", columnList = "created_by"),
    @Index(name = "idx_room_last_msg",   columnList = "last_message_at"),
    @Index(name = "idx_room_invite",     columnList = "invite_link", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {

    @Id
    @Column(name = "room_id", updatable = false, nullable = false, length = 36)
    private String roomId;

    /** Display name of the room */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private RoomType type = RoomType.GROUP;

    /** URL of the room icon/avatar */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** Max allowed members — default 100 for GROUP, 2 for DM */
    @Column(name = "max_members", nullable = false)
    @Builder.Default
    private int maxMembers = 100;

    /** userId of the creator */
    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    /** Unique join link (e.g. /join/abc123) */
    @Column(name = "invite_link", length = 100, unique = true)
    private String inviteLink;

    /** Updated whenever a message is sent — used to sort room list */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RoomMember> members = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.roomId == null) this.roomId = UUID.randomUUID().toString();
        if (this.inviteLink == null) this.inviteLink = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (this.type == RoomType.DM) this.maxMembers = 2;
    }
}
