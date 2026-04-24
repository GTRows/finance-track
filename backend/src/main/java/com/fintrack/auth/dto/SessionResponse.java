package com.fintrack.auth.dto;

import java.time.Instant;

public record SessionResponse(
        String id,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean current) {}
