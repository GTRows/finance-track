package com.fintrack.websocket;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes the latest asset prices to every connected STOMP client. Called by the price scheduler
 * after a successful refresh cycle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceBroadcaster {

    private static final String TOPIC = "/topic/prices";

    private final SimpMessagingTemplate messagingTemplate;
    private final AssetRepository assetRepository;

    /** Single price row delivered over the socket. */
    public record PriceUpdate(
            String symbol,
            String assetType,
            BigDecimal price,
            BigDecimal priceUsd,
            Instant updatedAt) {}

    /** Envelope so clients can cheaply tell messages apart. */
    public record PriceBatch(Instant publishedAt, int count, List<PriceUpdate> prices) {}

    /** Fetches every priced asset and broadcasts a single batch frame. */
    public void broadcastAll() {
        List<Asset> assets = assetRepository.findAllByOrderBySymbolAsc();
        List<PriceUpdate> updates =
                assets.stream()
                        .filter(a -> a.getPrice() != null)
                        .map(
                                a ->
                                        new PriceUpdate(
                                                a.getSymbol(),
                                                a.getAssetType().name(),
                                                a.getPrice(),
                                                a.getPriceUsd(),
                                                a.getPriceUpdatedAt()))
                        .toList();

        if (updates.isEmpty()) {
            return;
        }

        PriceBatch batch = new PriceBatch(Instant.now(), updates.size(), updates);
        try {
            messagingTemplate.convertAndSend(TOPIC, batch);
            log.debug("Broadcast {} prices to {}", updates.size(), TOPIC);
        } catch (Exception e) {
            log.warn("Price broadcast failed: {}", e.getMessage());
        }
    }
}
