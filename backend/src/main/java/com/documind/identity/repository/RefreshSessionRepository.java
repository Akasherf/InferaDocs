package com.documind.identity.repository;

import com.documind.identity.domain.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RefreshSession s WHERE s.tokenHash = :tokenHash AND s.revokedAt IS NULL AND s.expiresAt > :now")
    Optional<RefreshSession> findActiveByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Query("SELECT s FROM RefreshSession s WHERE s.tokenFamilyId = :familyId AND s.revokedAt IS NULL AND s.expiresAt > :now")
    Optional<RefreshSession> findActiveByFamilyId(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Query("SELECT s FROM RefreshSession s WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    List<RefreshSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT s FROM RefreshSession s WHERE s.userId = :userId ORDER BY s.createdAt DESC")
    List<RefreshSession> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshSession s SET s.revokedAt = :now WHERE s.id = :sessionId")
    int revokeById(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshSession s SET s.revokedAt = :now WHERE s.tokenFamilyId = :familyId AND s.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshSession s SET s.revokedAt = :now WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshSession s WHERE s.expiresAt < :now OR s.revokedAt IS NOT NULL")
    int cleanupExpiredOrRevoked(@Param("now") Instant now);
}