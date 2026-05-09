package com.fintrack.websocket;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes the latest asset prices to every connected STOMP client. Called by the price scheduler
 * after a successful refresh cycle.
 *
 * <p>The broadcaster keeps an in-memory snapshot of the prior tick's prices keyed by {@code
 * Asset.id} so it can emit only the assets whose {@code (price, priceUsd)} actually changed. The
 * first invocation after construction emits the full priced universe with {@code deltaOnly=false}
 * so a freshly-connected client always receives a snapshot of the current state. Subsequent ticks
 * emit a delta with {@code deltaOnly=true} or skip the broadcast entirely when nothing material has
 * changed (relative tolerance {@link #RELATIVE_TOLERANCE} = 0.01% suppresses last-decimal float
 * wiggle).
 *
 * <p>Snapshot memory: bounded by the asset master size (~80 entries today, ~200 cap per the cache
 * config). Memory cost: 200 entries × (UUID + 2 × BigDecimal + record overhead) ≈ 30 KB.
 *
 * <p>Thread safety: {@code broadcastAll()} is invoked from the price-scheduler thread and the
 * {@code ApplicationReadyEvent} thread; the snapshot map is {@link ConcurrentHashMap} and the
 * first-tick toggle is {@link AtomicBoolean}. No concurrent invocations occur in practice but the
 * primitives are correct under contention.
 *
 * <p>Asset removal is NOT broadcast. The frontend's price store keeps the stale value until the
 * next reconnect; emitting removal would require a new envelope flag and the frontend's price store
 * does not support hiding stale entries today.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceBroadcaster {

    private static final String TOPIC = "/topic/prices";

    /**
     * Relative tolerance (0.01%) for detecting material price changes between ticks. Smaller would
     * re-broadcast cosmetic noise from {@code setScale(4, HALF_UP)} rounding; larger would risk
     * missing real moves on stable-coin pairs.
     */
    private static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("0.0001");

    private final SimpMessagingTemplate messagingTemplate;
    private final AssetRepository assetRepository;

    private final ConcurrentHashMap<UUID, PriceSnapshot> lastSnapshot = new ConcurrentHashMap<>();
    private final AtomicBoolean firstTick = new AtomicBoolean(true);

    /** Single price row delivered over the socket. */
    public record PriceUpdate(
            String symbol,
            String assetType,
            BigDecimal price,
            BigDecimal priceUsd,
            Instant updatedAt) {}

    /**
     * Envelope so clients can cheaply tell messages apart. {@code totalAssets} carries the size of
     * the current price universe so the frontend can render "showing N changed of M total" UX text;
     * {@code deltaOnly} is {@code true} for the steady-state delta and {@code false} only for the
     * cold-boot full broadcast.
     */
    public record PriceBatch(
            Instant publishedAt,
            int count,
            int totalAssets,
            boolean deltaOnly,
            List<PriceUpdate> prices) {}

    /** Per-asset snapshot of the prior tick's pricing fields. */
    private record PriceSnapshot(BigDecimal price, BigDecimal priceUsd) {}

    /**
     * Reads every priced asset, diffs against the prior tick, and emits a {@link PriceBatch} to
     * {@value #TOPIC} carrying either the full priced universe (cold boot) or only the assets whose
     * {@code (price, priceUsd)} moved by more than {@link #RELATIVE_TOLERANCE} since the last tick.
     * Skips the broadcast entirely when a delta tick produces no material changes.
     */
    @Observed(name = "websocket.broadcast.prices", contextualName = "broadcastAll")
    public void broadcastAll() {
        List<Asset> assets = assetRepository.findAllByOrderBySymbolAsc();
        Map<UUID, Asset> currentByAssetId = new HashMap<>();
        List<PriceUpdate> pricedUpdates = new ArrayList<>();
        for (Asset asset : assets) {
            if (asset.getPrice() == null) continue;
            currentByAssetId.put(asset.getId(), asset);
            pricedUpdates.add(
                    new PriceUpdate(
                            asset.getSymbol(),
                            asset.getAssetType().name(),
                            asset.getPrice(),
                            asset.getPriceUsd(),
                            asset.getPriceUpdatedAt()));
        }

        if (currentByAssetId.isEmpty()) {
            return;
        }

        boolean firstRun = firstTick.compareAndSet(true, false);
        List<PriceUpdate> emit;
        boolean deltaOnly;
        if (firstRun) {
            emit = pricedUpdates;
            deltaOnly = false;
        } else {
            emit = new ArrayList<>();
            for (Map.Entry<UUID, Asset> entry : currentByAssetId.entrySet()) {
                Asset asset = entry.getValue();
                PriceSnapshot prior = lastSnapshot.get(entry.getKey());
                if (prior == null
                        || isMaterialChange(prior.price(), asset.getPrice())
                        || isMaterialChange(prior.priceUsd(), asset.getPriceUsd())) {
                    emit.add(
                            new PriceUpdate(
                                    asset.getSymbol(),
                                    asset.getAssetType().name(),
                                    asset.getPrice(),
                                    asset.getPriceUsd(),
                                    asset.getPriceUpdatedAt()));
                }
            }
            deltaOnly = true;
        }

        // Refresh the snapshot for the next tick BEFORE emit-skip so a no-change run still keeps
        // the prior snapshot stable if asset entries appeared/disappeared (currently can't happen
        // since assets are seeded globally and never removed).
        lastSnapshot.clear();
        currentByAssetId.forEach(
                (id, asset) ->
                        lastSnapshot.put(
                                id, new PriceSnapshot(asset.getPrice(), asset.getPriceUsd())));

        if (deltaOnly && emit.isEmpty()) {
            log.debug("No price changes since last tick; skipping broadcast");
            return;
        }

        PriceBatch batch =
                new PriceBatch(
                        Instant.now(), emit.size(), currentByAssetId.size(), deltaOnly, emit);
        try {
            messagingTemplate.convertAndSend(TOPIC, batch);
            log.debug(
                    "Broadcast {} prices (deltaOnly={}, totalAssets={}) to {}",
                    emit.size(),
                    deltaOnly,
                    currentByAssetId.size(),
                    TOPIC);
        } catch (Exception e) {
            log.warn("Price broadcast failed: {}", e.getMessage());
        }
    }

    /**
     * Returns true when {@code current} differs from {@code prior} by more than {@link
     * #RELATIVE_TOLERANCE} relative to {@code prior}, OR when either field transitions across null.
     * Falls back to a strict {@code current.signum() != 0} check when {@code prior} is zero so a
     * legitimate zero-to-non-zero move is not swallowed.
     */
    private static boolean isMaterialChange(BigDecimal prior, BigDecimal current) {
        if (prior == null && current == null) return false;
        if (prior == null || current == null) return true;
        if (prior.signum() == 0) return current.signum() != 0;
        BigDecimal diff = prior.subtract(current).abs();
        BigDecimal threshold = prior.abs().multiply(RELATIVE_TOLERANCE);
        return diff.compareTo(threshold) > 0;
    }
}
