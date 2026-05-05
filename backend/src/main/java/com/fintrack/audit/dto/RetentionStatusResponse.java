package com.fintrack.audit.dto;

import java.time.Instant;

public record RetentionStatusResponse(
        int retentionDays,
        int batchSize,
        boolean enabled,
        Instant oldestEntry,
        long totalEntries) {}
