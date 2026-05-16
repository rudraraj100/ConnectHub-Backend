package com.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User entity — §4.1 Auth/User-Service class diagram.
 * Stores userId, username, email, passwordHash, fullName,
 * avatarUrl, bio, status, provider, isActive, lastSeenAt, createdAt.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email",    columnList = "email",    unique = true),
        @Index(name = "idx_users_username", columnList = "username", unique = true),
        @Index(name = "idx_users_status",   columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    /** Null for OAuth2 users (they have no local password). */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(length = 500)
    private String bio;

    /** ONLINE / AWAY / DND / INVISIBLE — §2.2 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ONLINE;

    /** LOCAL / GOOGLE / GITHUB — §4.1 provider field */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    /** Platform-level role — MEMBER by default, PLATFORM_ADMIN only via direct DB query. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlatformRole role = PlatformRole.MEMBER;

    /** Subscription plan — FREE by default, PREMIUM via payment or manual SQL promotion. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlanType plan = PlanType.FREE;

    /** Custom presence text — only settable by PREMIUM users (e.g. "In a meeting 🎯"). */
    @Column(name = "custom_status", length = 100)
    private String customStatus;

    /** External OAuth2 provider user ID. */
    @Column(name = "provider_id", length = 100)
    private String providerId;

    /** Soft disable without deletion — §2.9 Platform Admin. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * True once the user clicks the email-verification link.
     * Always set to true for OAuth2 (Google) users — provider already verified the email.
     * LOCAL users start as false and cannot log in until verified.
     */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /** Updated on every WebSocket disconnect — §2.2 lastSeenAt. */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Location info — collected during registration */
    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;

    /** Dial prefix e.g. "+91" */
    @Column(name = "country_code", length = 10)
    private String countryCode;

    /** Phone number digits only — stored without dial prefix */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Auto-generate UUID primary key before first persist. */
    @PrePersist
    public void prePersist() {
        if (this.userId == null) {
            this.userId = UUID.randomUUID().toString();
        }
    }
}
