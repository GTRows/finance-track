package com.fintrack.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for the audit retention worker; bound from {@code fintrack.audit.*}. */
@ConfigurationProperties(prefix = "fintrack.audit")
public record AuditRetentionProperties(int retentionDays, int batchSize, boolean enabled) {

    public AuditRetentionProperties {
        if (retentionDays <= 0) retentionDays = 90;
        if (batchSize <= 0) batchSize = 1000;
    }
}
