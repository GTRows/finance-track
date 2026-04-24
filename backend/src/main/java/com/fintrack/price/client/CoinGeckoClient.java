package com.fintrack.price.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * CoinGecko simple price client. Issues a single batch request for all requested ids and returns
 * TRY/USD prices for each.
 */
@Component
@Slf4j
public class CoinGeckoClient {

    private final WebClient webClient;

    public CoinGeckoClient(@Qualifier("coinGeckoWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /** Returned price pair per asset: TRY first, USD second. Null values are possible. */
    public record PricePair(BigDecimal priceTry, BigDecimal priceUsd) {}

    /** Single price point in a historical series. */
    public record PricePoint(Instant at, BigDecimal price) {}

    /**
     * Fetches daily TRY price history for a CoinGecko id over the last {@code days}. Uses the
     * market_chart endpoint (daily granularity when days > 1).
     */
    public List<PricePoint> fetchHistory(String coingeckoId, int days) {
        if (coingeckoId == null || coingeckoId.isBlank()) return List.of();
        int window = Math.max(1, Math.min(days, 365));
        try {
            JsonNode response =
                    webClient
                            .get()
                            .uri(
                                    uri ->
                                            uri.path("/coins/{id}/market_chart")
                                                    .queryParam("vs_currency", "try")
                                                    .queryParam("days", window)
                                                    .build(coingeckoId))
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .timeout(Duration.ofSeconds(15))
                            .block();

            if (response == null || !response.has("prices") || !response.get("prices").isArray()) {
                return List.of();
            }

            List<PricePoint> result = new ArrayList<>();
            for (JsonNode row : response.get("prices")) {
                if (!row.isArray() || row.size() < 2) continue;
                long ts = row.get(0).asLong();
                BigDecimal price = row.get(1).decimalValue();
                result.add(new PricePoint(Instant.ofEpochMilli(ts), price));
            }
            return result;
        } catch (Exception e) {
            log.warn("CoinGecko history fetch failed for id={}: {}", coingeckoId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches spot prices for the given CoinGecko ids (e.g. "bitcoin", "ethereum"). Returns an
     * empty map on failure rather than throwing — the scheduler should keep running even if one
     * provider is down.
     */
    public Map<String, PricePair> fetchPrices(Collection<String> coingeckoIds) {
        if (coingeckoIds.isEmpty()) {
            return Map.of();
        }

        String ids = String.join(",", coingeckoIds);
        try {
            JsonNode response =
                    webClient
                            .get()
                            .uri(
                                    uri ->
                                            uri.path("/simple/price")
                                                    .queryParam("ids", ids)
                                                    .queryParam("vs_currencies", "try,usd")
                                                    .build())
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .timeout(Duration.ofSeconds(10))
                            .block();

            if (response == null || !response.isObject()) {
                log.warn("CoinGecko returned empty response for ids={}", ids);
                return Map.of();
            }

            Map<String, PricePair> result = new HashMap<>();
            response.fields()
                    .forEachRemaining(
                            entry -> {
                                JsonNode node = entry.getValue();
                                BigDecimal tryPrice =
                                        node.has("try") ? node.get("try").decimalValue() : null;
                                BigDecimal usdPrice =
                                        node.has("usd") ? node.get("usd").decimalValue() : null;
                                result.put(entry.getKey(), new PricePair(tryPrice, usdPrice));
                            });
            return result;
        } catch (Exception e) {
            log.warn("CoinGecko fetch failed for ids={}: {}", ids, e.getMessage());
            return Map.of();
        }
    }
}
