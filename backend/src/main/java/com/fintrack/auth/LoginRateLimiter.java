package com.fintrack.auth;

import com.fintrack.common.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed-window counter stored in Redis. Rejects a login attempt once either the
 * per-username or per-IP counter exceeds the configured threshold inside the window.
 *
 * <p>Keys expire automatically, so no scheduled cleanup is needed. Successful logins
 * clear the username-bound counter so the user is not punished for their own typos once
 * they get the password right.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginRateLimiter {

    private final StringRedisTemplate redis;

    @Value("${fintrack.auth.login-rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${fintrack.auth.login-rate-limit.window-seconds:300}")
    private long windowSeconds;

    /**
     * Rate limits for sensitive non-login actions (password reset, email verification,
     * token refresh). Keyed by IP + category so a single bad actor can't burn through
     * any one flow.
     */
    @Value("${fintrack.auth.sensitive-rate-limit.max-attempts:10}")
    private int sensitiveMaxAttempts;

    @Value("${fintrack.auth.sensitive-rate-limit.window-seconds:600}")
    private long sensitiveWindowSeconds;

    public void enforce(String username) {
        if (!isEnabled()) return;
        String ip = RequestContext.clientIp();
        if (isBlocked("login:user:" + normalise(username)) || (ip != null && isBlocked("login:ip:" + ip))) {
            log.warn("Login rate limit hit for user={} ip={}", username, ip);
            throw new LoginRateLimitException(
                    "Too many login attempts. Try again in a few minutes.");
        }
    }

    /**
     * Enforce rate limit for sensitive flows by category (e.g. "password-reset",
     * "email-verify", "token-refresh"). Uses a per-IP counter so legitimate users on
     * a different network are unaffected by abuse from a single source.
     */
    public void enforceSensitive(String category) {
        if (!isSensitiveEnabled()) return;
        String ip = RequestContext.clientIp();
        if (ip == null) return;
        String key = "sensitive:" + category + ":ip:" + ip;
        String raw = redis.opsForValue().get(key);
        int count = 0;
        try {
            if (raw != null) count = Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        if (count >= sensitiveMaxAttempts) {
            log.warn("Sensitive rate limit hit for category={} ip={}", category, ip);
            throw new LoginRateLimitException(
                    "Too many attempts. Try again in a few minutes.");
        }
        Long next = redis.opsForValue().increment(key);
        if (next != null && next == 1L) {
            redis.expire(key, Duration.ofSeconds(sensitiveWindowSeconds));
        }
    }

    public void recordFailure(String username) {
        if (!isEnabled()) return;
        increment("login:user:" + normalise(username));
        String ip = RequestContext.clientIp();
        if (ip != null) {
            increment("login:ip:" + ip);
        }
    }

    public void recordSuccess(String username) {
        if (!isEnabled()) return;
        redis.delete("login:user:" + normalise(username));
    }

    private boolean isBlocked(String key) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) return false;
        try {
            return Long.parseLong(raw) >= maxAttempts;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
    }

    private boolean isEnabled() {
        return maxAttempts > 0 && windowSeconds > 0;
    }

    private boolean isSensitiveEnabled() {
        return sensitiveMaxAttempts > 0 && sensitiveWindowSeconds > 0;
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
