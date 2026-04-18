package com.fintrack.audit;

import com.fintrack.common.entity.AuditLog;
import com.fintrack.common.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        String ip = RequestContext.clientIp();
        String ua = truncate(RequestContext.userAgent(), USER_AGENT_MAX);
        String safeDetail = truncate(detail, DETAIL_MAX);

        AuditLog entry = AuditLog.builder()
                .action(action)
                .status(status)
                .userId(userId)
                .username(username)
                .detail(safeDetail)
                .ipAddress(ip)
                .userAgent(ua)
                .build();
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed for action={}: {}", action, e.getMessage());
        }

        log.info("AUDIT action={} status={} user=\"{}\" ip={} detail=\"{}\"",
                action,
                status,
                username == null ? "" : username,
                ip == null ? "" : ip,
                safeDetail == null ? "" : safeDetail.replace("\"", "'"));
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

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
