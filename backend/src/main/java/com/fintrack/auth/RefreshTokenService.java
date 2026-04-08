package com.fintrack.auth;

import com.fintrack.common.entity.RefreshToken;
import com.fintrack.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Manages refresh token lifecycle: creation, validation, rotation, and revocation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    /**
     * Creates and persists a new refresh token for the given user.
     *
     * @param userId the user's UUID
     * @return the raw refresh token string
     */
    @Transactional
    public String createRefreshToken(UUID userId) {
        String tokenValue = jwtUtil.generateRefreshToken(userId.toString());
        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .token(tokenValue)
                .expiresAt(Instant.now().plusMillis(jwtUtil.getRefreshExpiryMs()))
                .build();
        refreshTokenRepository.save(entity);
        log.debug("Created refresh token for user {}", userId);
        return tokenValue;
    }

    /**
     * Validates a refresh token: must exist in DB and not be expired.
     *
     * @param token the raw refresh token string
     * @return the RefreshToken entity
     * @throws BusinessRuleException if the token is invalid or expired
     */
    @Transactional(readOnly = true)
    public RefreshToken validate(String token) {
        RefreshToken entity = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessRuleException("Invalid refresh token", "INVALID_REFRESH_TOKEN"));
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Refresh token expired", "REFRESH_TOKEN_EXPIRED");
        }
        return entity;
    }

    /**
     * Rotates a refresh token: deletes the old one and creates a new one.
     *
     * @param oldToken the current refresh token string
     * @param userId   the user's UUID
     * @return the new refresh token string
     */
    @Transactional
    public String rotate(String oldToken, UUID userId) {
        refreshTokenRepository.deleteByToken(oldToken);
        return createRefreshToken(userId);
    }

    /** Revokes a single refresh token (logout). */
    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.deleteByToken(token);
        log.debug("Revoked refresh token");
    }

    /** Revokes all refresh tokens for a user (forced logout from all devices). */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
        log.debug("Revoked all refresh tokens for user {}", userId);
    }

    /** Deletes expired tokens (called by cleanup scheduler). */
    @Transactional
    public int cleanupExpired() {
        int deleted = refreshTokenRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh tokens", deleted);
        }
        return deleted;
    }
}
