package com.documind.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions",
        indexes = {
                @Index(name = "refresh_sessions_token_hash_idx", columnList = "token_hash"),
                @Index(name = "refresh_sessions_user_idx", columnList = "user_id"),
                @Index(name = "refresh_sessions_family_idx", columnList = "token_family_id"),
                @Index(name = "refresh_sessions_expires_idx", columnList = "expires_at"),
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshSession {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_family_id", nullable = false)
    private UUID tokenFamilyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "device_label", length = 255)
    private String deviceLabel;

    @Column(name = "rotation_count", nullable = false)
    private Integer rotationCount = 0;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now()) || expiresAt.equals(Instant.now());
    }
}