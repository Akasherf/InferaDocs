package com.documind.audit.repository;

import com.documind.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("SELECT a FROM AuditEvent a WHERE a.actorUserId = :userId ORDER BY a.createdAt DESC")
    Page<AuditEvent> findByActorUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.organizationId = :orgId ORDER BY a.createdAt DESC")
    Page<AuditEvent> findByOrganizationId(@Param("orgId") UUID orgId, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.requestId = :requestId ORDER BY a.createdAt DESC")
    List<AuditEvent> findByRequestId(@Param("requestId") UUID requestId);

    @Query("SELECT a FROM AuditEvent a WHERE a.eventType = :eventType ORDER BY a.createdAt DESC")
    Page<AuditEvent> findByEventType(@Param("eventType") String eventType, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    Page<AuditEvent> findByDateRange(@Param("start") Instant start, @Param("end") Instant end, Pageable pageable);
}