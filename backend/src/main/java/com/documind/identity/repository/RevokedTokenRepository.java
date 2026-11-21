package com.documind.identity.repository;

import com.documind.identity.domain.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    @Query("SELECT r FROM RevokedToken r WHERE r.jti = :jti")
    Optional<RevokedToken> findByJti(@Param("jti") String jti);

    boolean existsByJti(String jti);
}