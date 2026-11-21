package com.documind.common.repository;

import com.documind.common.security.TenantAware;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface TenantAwareRepository<T extends TenantAware, ID> extends JpaRepository<T, ID> {

    @Query("SELECT e FROM #{#entityName} e WHERE e.organizationId = :organizationId AND e.id = :id")
    Optional<T> findByIdAndOrganizationId(@Param("id") ID id, @Param("organizationId") UUID organizationId);

    @Query("SELECT e FROM #{#entityName} e WHERE e.organizationId = :organizationId")
    List<T> findAllByOrganizationId(@Param("organizationId") UUID organizationId);

    boolean existsByOrganizationId(UUID organizationId);
}