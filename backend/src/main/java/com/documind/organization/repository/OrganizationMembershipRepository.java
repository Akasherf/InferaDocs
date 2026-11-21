package com.documind.organization.repository;

import com.documind.organization.domain.OrganizationMembership;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    @Query("SELECT m FROM OrganizationMembership m WHERE m.organizationId = :organizationId AND m.userId = :userId AND m.status = 'ACTIVE'")
    Optional<OrganizationMembership> findActiveByOrganizationIdAndUserId(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId
    );

    @Query("SELECT m FROM OrganizationMembership m WHERE m.organizationId = :organizationId AND m.status = 'ACTIVE'")
    List<OrganizationMembership> findActiveByOrganizationId(@Param("organizationId") UUID organizationId);

    @Query("SELECT m FROM OrganizationMembership m WHERE m.userId = :userId AND m.status = 'ACTIVE'")
    List<OrganizationMembership> findActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(m) FROM OrganizationMembership m WHERE m.organizationId = :organizationId AND m.role = 'OWNER' AND m.status = 'ACTIVE'")
    long countActiveOwners(@Param("organizationId") UUID organizationId);

    @Query("SELECT m FROM OrganizationMembership m WHERE m.organizationId = :organizationId AND m.role = 'OWNER' AND m.status = 'ACTIVE'")
    List<OrganizationMembership> findActiveOwners(@Param("organizationId") UUID organizationId);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM OrganizationMembership m WHERE m.id = :id")
    Optional<OrganizationMembership> findByIdForUpdate(@Param("id") UUID id);
}