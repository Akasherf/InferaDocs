package com.documind.organization.domain;

import com.documind.common.security.TenantAware;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_memberships",
        indexes = {
                @Index(name = "organization_memberships_user_idx", columnList = "user_id"),
                @Index(name = "organization_memberships_org_idx", columnList = "organization_id"),
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "organization_memberships_org_user_key", columnNames = {"organization_id", "user_id"})
        })
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "organizationId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "organization_id = :organizationId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMembership implements TenantAware {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum MembershipRole {
        OWNER, ADMIN, MEMBER, VIEWER
    }

    public enum MembershipStatus {
        ACTIVE, SUSPENDED
    }

    public boolean isActive() {
        return this.status == MembershipStatus.ACTIVE;
    }

    public boolean isOwner() {
        return this.role == MembershipRole.OWNER;
    }

    public boolean isAdminOrAbove() {
        return this.role == MembershipRole.OWNER || this.role == MembershipRole.ADMIN;
    }
}