package com.fintrack.auth;

import com.fintrack.common.entity.RefreshToken;
import com.fintrack.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        return createRefreshToken(userId, null, null);
    }

    /** Creates a refresh token and records the originating device metadata. */
    @Transactional
    public String createRefreshToken(UUID userId, String userAgent, String ipAddress) {
        String tokenValue = jwtUtil.generateRefreshToken(userId.toString());
        Instant now = Instant.now();
        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .token(tokenValue)
                .userAgent(truncate(userAgent, 512))
                .ipAddress(truncate(ipAddress, 45))
                .lastUsedAt(now)
                .expiresAt(now.plusMillis(jwtUtil.getRefreshExpiryMs()))
                .build();
        refreshTokenRepository.save(entity);
        log.debug("Created refresh token for user {}", userId);
        return tokenValue;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
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
        return rotate(oldToken, userId, null, null);
    }

    /** Rotates while carrying over device metadata from the inbound request. */
    @Transactional
    public String rotate(String oldToken, UUID userId, String userAgent, String ipAddress) {
        RefreshToken existing = refreshTokenRepository.findByToken(oldToken).orElse(null);
        String ua = userAgent != null ? userAgent : existing != null ? existing.getUserAgent() : null;
        String ip = ipAddress != null ? ipAddress : existing != null ? existing.getIpAddress() : null;
        refreshTokenRepository.deleteByToken(oldToken);
        return createRefreshToken(userId, ua, ip);
    }

    /** Lists active (non-expired) refresh tokens for a user. */
    @Transactional(readOnly = true)
    public List<RefreshToken> listActive(UUID userId) {
        return refreshTokenRepository.findByUserIdAndExpiresAtAfterOrderByLastUsedAtDesc(userId, Instant.now());
    }

    /** Revokes one session owned by the user. Returns true if it existed. */
    @Transactional
    public boolean revokeSession(UUID userId, UUID sessionId) {
        return refreshTokenRepository.findByIdAndUserId(sessionId, userId)
                .map(rt -> {
                    refreshTokenRepository.delete(rt);
                    return true;
                })
                .orElse(false);
    }

    /** Revokes every other session for the user, keeping the supplied one. Returns the count removed. */
    @Transactional
    public int revokeOthers(UUID userId, UUID keepSessionId) {
        return refreshTokenRepository.deleteByUserIdExcept(userId, keepSessionId);
    }

    /** Returns the userId for a token if it exists, without validating expiry. */
    @Transactional(readOnly = true)
    public Optional<UUID> peekUserId(String token) {
        return refreshTokenRepository.findByToken(token).map(RefreshToken::getUserId);
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
