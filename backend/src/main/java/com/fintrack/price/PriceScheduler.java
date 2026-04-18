package com.fintrack.price;

import com.fintrack.alert.PriceAlertService;
import com.fintrack.websocket.PriceBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Periodically refreshes prices so the portfolio UI always has fresh data
 * without needing a manual trigger.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceScheduler {

    private final PriceSyncService priceSyncService;
    private final PriceBroadcaster priceBroadcaster;
    private final PriceAlertService priceAlertService;

    private void evaluateAlertsSafely() {
        try {
            priceAlertService.evaluateAll();
        } catch (Exception e) {
            log.warn("Price alert evaluation failed: {}", e.getMessage());
        }
    }

    /** Kick off an initial refresh once the application has finished starting. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            PriceSyncService.SyncResult result = priceSyncService.refreshAll();
            log.info("Initial price sync complete: crypto={} currency={} fund={} metal={}",
                    result.cryptoUpdated(), result.currencyUpdated(),
                    result.fundUpdated(), result.metalUpdated());
            priceBroadcaster.broadcastAll();
            evaluateAlertsSafely();
        } catch (Exception e) {
            log.warn("Initial price sync failed: {}", e.getMessage());
        }
    }

    /** Runs on a fixed delay defined by {@code price-api.sync-interval-seconds}. */
    @Scheduled(fixedDelayString = "${price-api.sync-interval-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void scheduledRefresh() {
        try {
            PriceSyncService.SyncResult result = priceSyncService.refreshLive();
            log.debug("Scheduled price sync: crypto={} currency={} metal={}",
                    result.cryptoUpdated(), result.currencyUpdated(), result.metalUpdated());
            if (result.cryptoUpdated() + result.currencyUpdated() + result.metalUpdated() > 0) {
                priceBroadcaster.broadcastAll();
                evaluateAlertsSafely();
            }
        } catch (Exception e) {
            log.warn("Scheduled price sync failed: {}", e.getMessage());
        }
    }

    /**
     * TEFAS fund prices only publish once per trading day, so we only poke them
     * every hour. A separate schedule keeps the 30-second tick free of slow
     * sequential HTTP calls.
     */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void scheduledFundRefresh() {
        try {
            int updated = priceSyncService.refreshFunds();
            log.debug("Scheduled fund sync: updated={}", updated);
            if (updated > 0) {
                priceBroadcaster.broadcastAll();
                evaluateAlertsSafely();
            }
        } catch (Exception e) {
            log.warn("Scheduled fund sync failed: {}", e.getMessage());
        }
    }
}
