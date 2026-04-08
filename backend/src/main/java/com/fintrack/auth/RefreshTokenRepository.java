package com.fintrack.auth;

import com.fintrack.common.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for refresh token CRUD and cleanup.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Find a refresh token by its raw value. */
    Optional<RefreshToken> findByToken(String token);

    /** Delete all refresh tokens for a user (forced logout). */
    @Modifying
    void deleteByUserId(UUID userId);

    /** Delete a specific token (single session logout). */
    @Modifying
    void deleteByToken(String token);

    /** Cleanup expired tokens. */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
