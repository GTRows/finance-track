package com.fintrack.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT token generation and validation utility.
 * Access tokens carry user identity; refresh tokens are opaque references.
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessExpiryMs;
    private final long refreshExpiryMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiry-minutes:15}") long accessMinutes,
            @Value("${jwt.refresh-expiry-days:30}") long refreshDays) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiryMs = accessMinutes * 60 * 1000;
        this.refreshExpiryMs = refreshDays * 24 * 60 * 60 * 1000;
    }

    /**
     * Generates a short-lived access token containing user claims.
     *
     * @param userId   the user's UUID
     * @param username the user's login name
     * @param role     the user's role (USER or ADMIN)
     * @return signed JWT string
     */
    public String generateAccessToken(String userId, String username, String role) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .claim("type", "ACCESS")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiryMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generates a long-lived refresh token for token rotation.
     *
     * @param userId the user's UUID
     * @return signed JWT string
     */
    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("type", "REFRESH")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiryMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validates the token signature and expiry, returning parsed claims.
     *
     * @param token the JWT string
     * @return parsed claims
     * @throws JwtException if the token is invalid
     */
    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Returns true if the token is valid and not expired. */
    public boolean isValid(String token) {
        try {
            validateAndParse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /** Extracts the user ID (subject) from a valid token. */
    public String getUserId(String token) {
        return validateAndParse(token).getSubject();
    }

    /** Extracts the username claim from a valid token. */
    public String getUsername(String token) {
        return validateAndParse(token).get("username", String.class);
    }

    /** Extracts the role claim from a valid token. */
    public String getRole(String token) {
        return validateAndParse(token).get("role", String.class);
    }

    /** Returns the access token expiry in seconds. */
    public long getAccessExpirySeconds() {
        return accessExpiryMs / 1000;
    }

    /** Returns the refresh token expiry in milliseconds. */
    public long getRefreshExpiryMs() {
        return refreshExpiryMs;
    }
}
