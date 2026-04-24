package com.fintrack.audit.dto;

import com.fintrack.common.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        UUID userId,
        String username,
        String action,
        String status,
        String ipAddress,
        String userAgent,
        String detail,
        Instant createdAt) {
    public static AuditLogResponse from(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getUserId(),
                entry.getUsername(),
                entry.getAction(),
                entry.getStatus().name(),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.getDetail(),
                entry.getCreatedAt());
    }
}
