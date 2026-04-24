package com.fintrack.portfolio.snapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron-triggered daily snapshots. Runs once at 00:05 Europe/Istanbul so the previous trading day is
 * fully captured, and also runs a backfill on startup so a freshly-booted container always has
 * today's data point.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SnapshotScheduler {

    private final SnapshotService snapshotService;

    /** Runs a few minutes after midnight in Istanbul time. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Istanbul")
    public void captureAtMidnight() {
        try {
            SnapshotService.CaptureResult result = snapshotService.captureDaily();
            log.info(
                    "Nightly snapshot capture: date={} created={} updated={}",
                    result.date(),
                    result.created(),
                    result.updated());
        } catch (Exception e) {
            log.error("Nightly snapshot capture failed: {}", e.getMessage(), e);
        }
    }

    /** Captures today's snapshot once the application has finished starting. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            SnapshotService.CaptureResult result = snapshotService.captureDaily();
            log.info(
                    "Startup snapshot capture: date={} created={} updated={}",
                    result.date(),
                    result.created(),
                    result.updated());
        } catch (Exception e) {
            log.warn("Startup snapshot capture failed: {}", e.getMessage());
        }
    }
}
