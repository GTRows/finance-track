package com.fintrack.price.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * CoinGecko simple price client. Issues a single batch request for all
 * requested ids and returns TRY/USD prices for each.
 */
@Component
@Slf4j
public class CoinGeckoClient {

    private final WebClient webClient;

    public CoinGeckoClient(@Qualifier("coinGeckoWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /** Returned price pair per asset: TRY first, USD second. Null values are possible. */
    public record PricePair(BigDecimal priceTry, BigDecimal priceUsd) {
    }

    /**
     * Fetches spot prices for the given CoinGecko ids (e.g. "bitcoin", "ethereum").
     * Returns an empty map on failure rather than throwing — the scheduler should
     * keep running even if one provider is down.
     */
    public Map<String, PricePair> fetchPrices(Collection<String> coingeckoIds) {
        if (coingeckoIds.isEmpty()) {
            return Map.of();
        }

        String ids = String.join(",", coingeckoIds);
        try {
            JsonNode response = webClient.get()
                    .uri(uri -> uri.path("/simple/price")
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
            response.fields().forEachRemaining(entry -> {
                JsonNode node = entry.getValue();
                BigDecimal tryPrice = node.has("try") ? node.get("try").decimalValue() : null;
                BigDecimal usdPrice = node.has("usd") ? node.get("usd").decimalValue() : null;
                result.put(entry.getKey(), new PricePair(tryPrice, usdPrice));
            });
            return result;
        } catch (Exception e) {
            log.warn("CoinGecko fetch failed for ids={}: {}", ids, e.getMessage());
            return Map.of();
        }
    }
}
