package com.fintrack.auth;

import com.fintrack.common.entity.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for refresh token CRUD and cleanup. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Find a refresh token by its raw value. */
    Optional<RefreshToken> findByToken(String token);

    /** Active (non-expired) tokens for a user, newest first. */
    List<RefreshToken> findByUserIdAndExpiresAtAfterOrderByLastUsedAtDesc(
            UUID userId, Instant cutoff);

    /** Find a token by id scoped to a user (for authorized single-session revoke). */
    Optional<RefreshToken> findByIdAndUserId(UUID id, UUID userId);

    /** Delete all refresh tokens for a user (forced logout). */
    @Modifying
    void deleteByUserId(UUID userId);

    /**
     * Delete all refresh tokens for a user except one (e.g. keep current session after password
     * change).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userId = :userId AND rt.id <> :keepId")
    int deleteByUserIdExcept(@Param("userId") UUID userId, @Param("keepId") UUID keepId);

    /** Delete a specific token (single session logout). */
    @Modifying
    void deleteByToken(String token);

    /** Cleanup expired tokens. */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
