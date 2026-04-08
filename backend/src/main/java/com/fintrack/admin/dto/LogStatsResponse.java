package com.fintrack.admin.dto;

import java.time.Instant;

/**
 * Aggregate statistics about log files.
 */
public record LogStatsResponse(
        long totalSizeBytes,
        int fileCount,
        Instant oldestFile,
        Instant newestFile
) {
}
