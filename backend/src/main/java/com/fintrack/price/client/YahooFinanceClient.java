package com.fintrack.price.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Yahoo Finance chart API client. Keyless public endpoint that exposes a regular market price +
 * currency for any ticker symbol including BIST via the {@code .IS} suffix (e.g. {@code THYAO.IS}).
 * Used for individual stock coverage where TEFAS only lists fund-style instruments.
 */
@Component
@Slf4j
public class YahooFinanceClient {

    private static final String BASE_URL = "https://query1.finance.yahoo.com";
    private static final String UA =
            "Mozilla/5.0 (compatible; FinTrackPro/1.0; +https://github.com/)";

    private final WebClient webClient;

    public YahooFinanceClient() {
        this.webClient =
                WebClient.builder()
                        .baseUrl(BASE_URL)
                        .defaultHeader("User-Agent", UA)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    /** Current market price and its native currency for a given Yahoo symbol. */
    public record Quote(BigDecimal price, String currency) {}

    /** Timestamped close price. */
    public record PricePoint(Instant at, BigDecimal price) {}

    /** Fetches current quotes for a batch of symbols in parallel, one request each. */
    public Map<String, Quote> fetchQuotes(Iterable<String> symbols) {
        Map<String, Quote> out = new HashMap<>();
        for (String raw : symbols) {
            if (raw == null) continue;
            String symbol = raw.trim();
            if (symbol.isEmpty()) continue;
            Quote quote = fetchOne(symbol);
            if (quote != null) out.put(symbol, quote);
        }
        return out;
    }

    private Quote fetchOne(String symbol) {
        try {
            JsonNode response =
                    webClient
                            .get()
                            .uri(
                                    uri ->
                                            uri.path("/v8/finance/chart/{symbol}")
                                                    .queryParam("interval", "1d")
                                                    .queryParam("range", "1d")
                                                    .build(symbol))
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .timeout(Duration.ofSeconds(10))
                            .block();

            JsonNode meta =
                    response == null
                            ? null
                            : response.path("chart").path("result").path(0).path("meta");
            if (meta == null || meta.isMissingNode()) {
                log.warn("Yahoo quote {}: missing meta in response", symbol);
                return null;
            }
            JsonNode priceNode = meta.path("regularMarketPrice");
            if (!priceNode.isNumber() || priceNode.decimalValue().signum() <= 0) {
                log.warn("Yahoo quote {}: no usable regularMarketPrice", symbol);
                return null;
            }
            String currency = meta.path("currency").asText(null);
            return new Quote(priceNode.decimalValue(), currency);
        } catch (Exception e) {
            log.warn("Yahoo quote {} fetch failed: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches daily close history over the last N days. Uses 1d interval with a range derived from
     * {@code days}. Caller receives epoch-seconds timestamps already translated to {@link Instant}.
     */
    public List<PricePoint> fetchHistory(String symbol, int days) {
        if (symbol == null || symbol.isBlank()) return List.of();
        int window = Math.max(1, Math.min(days, 365));
        String range =
                window <= 5
                        ? "5d"
                        : window <= 30
                                ? "1mo"
                                : window <= 90
                                        ? "3mo"
                                        : window <= 180 ? "6mo" : window <= 365 ? "1y" : "2y";
        try {
            JsonNode response =
                    webClient
                            .get()
                            .uri(
                                    uri ->
                                            uri.path("/v8/finance/chart/{symbol}")
                                                    .queryParam("interval", "1d")
                                                    .queryParam("range", range)
                                                    .build(symbol))
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .timeout(Duration.ofSeconds(15))
                            .block();

            JsonNode result =
                    response == null ? null : response.path("chart").path("result").path(0);
            if (result == null || result.isMissingNode()) return List.of();

            JsonNode timestamps = result.path("timestamp");
            JsonNode closes = result.path("indicators").path("quote").path(0).path("close");
            if (!timestamps.isArray() || !closes.isArray()) return List.of();

            List<PricePoint> points = new ArrayList<>();
            int size = Math.min(timestamps.size(), closes.size());
            for (int i = 0; i < size; i++) {
                JsonNode closeNode = closes.get(i);
                if (closeNode == null || closeNode.isNull() || !closeNode.isNumber()) continue;
                long epochSecond = timestamps.get(i).asLong();
                points.add(
                        new PricePoint(
                                Instant.ofEpochSecond(epochSecond), closeNode.decimalValue()));
            }
            return points;
        } catch (Exception e) {
            log.warn("Yahoo history {} fetch failed: {}", symbol, e.getMessage());
            return List.of();
        }
    }
}
