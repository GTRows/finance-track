package com.fintrack.audit;

import com.fintrack.common.entity.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final int USER_AGENT_MAX = 255;
    private static final int DETAIL_MAX = 500;

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, AuditLog.Status status, UUID userId, String username, String detail) {
        AuditLog entry = AuditLog.builder()
                .action(action)
                .status(status)
                .userId(userId)
                .username(username)
                .detail(truncate(detail, DETAIL_MAX))
                .ipAddress(currentIp())
                .userAgent(truncate(currentUserAgent(), USER_AGENT_MAX))
                .build();
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed for action={}: {}", action, e.getMessage());
        }
    }

    public void success(String action, UUID userId, String username) {
        record(action, AuditLog.Status.SUCCESS, userId, username, null);
    }

    public void success(String action, UUID userId, String username, String detail) {
        record(action, AuditLog.Status.SUCCESS, userId, username, detail);
    }

    public void failure(String action, String username, String detail) {
        record(action, AuditLog.Status.FAILURE, null, username, detail);
    }

    public void failure(String action, UUID userId, String username, String detail) {
        record(action, AuditLog.Status.FAILURE, userId, username, detail);
    }

    private String currentIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader("User-Agent") : null;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
