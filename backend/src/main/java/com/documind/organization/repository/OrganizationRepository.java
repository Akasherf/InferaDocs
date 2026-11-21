package com.documind.organization.repository;

import com.documind.organization.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT o FROM Organization o WHERE o.id = :id AND o.status = 'ACTIVE'")
    Optional<Organization> findActiveById(@Param("id") UUID id);

    @Query("SELECT o FROM Organization o WHERE o.createdBy = :userId AND o.status = 'ACTIVE'")
    List<Organization> findActiveByCreatedBy(@Param("userId") UUID userId);

    @Query("SELECT o FROM Organization o WHERE o.status = 'ACTIVE'")
    List<Organization> findAllActive();
}