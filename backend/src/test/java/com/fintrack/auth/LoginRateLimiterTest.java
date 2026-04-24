package com.fintrack.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    @InjectMocks LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(limiter, "maxAttempts", 3);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 60L);
        ReflectionTestUtils.setField(limiter, "sensitiveMaxAttempts", 10);
        ReflectionTestUtils.setField(limiter, "sensitiveWindowSeconds", 600L);
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void enforceAllowsWhenBelowThreshold() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("login:user:ali")).thenReturn("2");

        assertDoesNotThrow(() -> limiter.enforce("ali"));
    }

    @Test
    void enforceBlocksAtThreshold() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("login:user:ali")).thenReturn("3");

        assertThrows(LoginRateLimitException.class, () -> limiter.enforce("ali"));
    }

    @Test
    void recordFailureSetsExpiryOnFirstIncrement() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment("login:user:ali")).thenReturn(1L);

        limiter.recordFailure("ali");

        verify(redis).expire(eq("login:user:ali"), any(Duration.class));
    }

    @Test
    void recordFailureDoesNotResetExpiry() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment("login:user:ali")).thenReturn(2L);

        limiter.recordFailure("ali");

        verify(redis, org.mockito.Mockito.never()).expire(any(String.class), any(Duration.class));
    }

    @Test
    void recordSuccessClearsUserCounter() {
        limiter.recordSuccess("ali");

        verify(redis).delete("login:user:ali");
    }

    @Test
    void usernameIsCaseInsensitive() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("login:user:ali")).thenReturn("5");

        assertThrows(LoginRateLimitException.class, () -> limiter.enforce("ALI"));
    }

    @Test
    void enforceSensitiveAllowsBelowThresholdAndIncrementsCounter() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("sensitive:password-reset:ip:10.0.0.1")).thenReturn("3");
        when(ops.increment("sensitive:password-reset:ip:10.0.0.1")).thenReturn(4L);

        assertDoesNotThrow(() -> limiter.enforceSensitive("password-reset"));

        verify(ops).increment("sensitive:password-reset:ip:10.0.0.1");
    }

    @Test
    void enforceSensitiveSetsExpiryOnFirstIncrement() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("sensitive:email-verify:ip:10.0.0.1")).thenReturn(null);
        when(ops.increment("sensitive:email-verify:ip:10.0.0.1")).thenReturn(1L);

        limiter.enforceSensitive("email-verify");

        verify(redis).expire(eq("sensitive:email-verify:ip:10.0.0.1"), any(Duration.class));
    }

    @Test
    void enforceSensitiveBlocksAtThreshold() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("sensitive:password-reset:ip:10.0.0.1")).thenReturn("10");

        assertThrows(LoginRateLimitException.class,
                () -> limiter.enforceSensitive("password-reset"));
        verify(ops, org.mockito.Mockito.never()).increment(any(String.class));
    }
}
