package com.documind.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "revoked_tokens",
        indexes = {
                @Index(name = "revoked_tokens_jti_idx", columnList = "jti", unique = true),
                @Index(name = "revoked_tokens_revoked_at_idx", columnList = "revoked_at"),
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "jti", nullable = false, length = 36, unique = true)
    private String jti;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reason", length = 100)
    private String reason;
}