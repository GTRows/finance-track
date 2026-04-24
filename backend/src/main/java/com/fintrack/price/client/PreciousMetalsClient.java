package com.fintrack.price.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spot precious-metal prices from api.gold-api.com. Keyless public endpoint that returns USD per
 * troy ounce for each symbol (XAU/XAG/XPT/XPD). Callers are responsible for converting to local
 * currency using a separate FX rate.
 */
@Component
@Slf4j
public class PreciousMetalsClient {

    private static final String BASE_URL = "https://api.gold-api.com";

    private final WebClient webClient;

    public PreciousMetalsClient() {
        this.webClient = WebClient.builder().baseUrl(BASE_URL).build();
    }

    /** Returns USD-per-ounce for each requested metal symbol. */
    public Map<String, BigDecimal> fetchUsdPerOunce(Iterable<String> symbols) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (String raw : symbols) {
            if (raw == null) continue;
            String symbol = raw.trim().toUpperCase();
            if (symbol.isEmpty()) continue;
            BigDecimal price = fetchOne(symbol);
            if (price != null) out.put(symbol, price);
        }
        return out;
    }

    private BigDecimal fetchOne(String symbol) {
        try {
            JsonNode response =
                    webClient
                            .get()
                            .uri("/price/{symbol}", symbol)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .timeout(Duration.ofSeconds(10))
                            .block();

            if (response == null) return null;
            JsonNode price = response.path("price");
            if (!price.isNumber() || price.decimalValue().signum() <= 0) {
                log.warn("Precious metal {}: no usable price in response", symbol);
                return null;
            }
            return price.decimalValue();
        } catch (Exception e) {
            log.warn("Precious metal {} fetch failed: {}", symbol, e.getMessage());
            return null;
        }
    }
}
