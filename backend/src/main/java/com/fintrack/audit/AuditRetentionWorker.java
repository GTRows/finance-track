package com.fintrack.audit;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily retention sweeper for {@code audit_log}. Deletes rows older than {@link
 * AuditRetentionProperties#retentionDays()} in chunks of {@link
 * AuditRetentionProperties#batchSize()} so each delete commits independently and the table is never
 * held under a long lock. A 100-iteration safety cap bounds runaway deletion in pathological cases.
 * Method is intentionally NOT {@code @Transactional}: each {@code deleteOldestBatch} call commits
 * in its own short transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditRetentionWorker {

    private static final int MAX_ITERATIONS = 100;

    private final AuditLogRepository auditLogRepository;
    private final AuditRetentionProperties properties;
    private final AuditService auditService;

    @PostConstruct
    void init() {
        log.info(
                "Audit retention configured: enabled={} retentionDays={} batchSize={}",
                properties.enabled(),
                properties.retentionDays(),
                properties.batchSize());
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void prune() {
        if (!properties.enabled()) {
            log.info("Audit retention disabled; skipping prune");
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.retentionDays()));
        int totalDeleted = 0;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            int deleted = auditLogRepository.deleteOldestBatch(cutoff, properties.batchSize());
            totalDeleted += deleted;
            if (deleted == 0) break;
        }
        log.info("Audit retention pruned {} rows older than {}", totalDeleted, cutoff);
        auditService.success(
                AuditAction.AUDIT_RETENTION_PRUNED,
                null,
                "system",
                String.format("deleted=%d cutoff=%s", totalDeleted, cutoff));
    }
}
