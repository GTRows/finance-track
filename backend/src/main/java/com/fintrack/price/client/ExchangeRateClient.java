package com.fintrack.price.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.price.PriceApiProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Exchange rate client backed by exchangerate-api.com. Returns TRY rates for a small set of base
 * currencies. Requires an API key in configuration.
 */
@Component
@Slf4j
public class ExchangeRateClient {

    private final WebClient webClient;
    private final PriceApiProperties props;

    public ExchangeRateClient(
            @Qualifier("exchangeRateWebClient") WebClient webClient, PriceApiProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    /**
     * Returns the TRY price of 1 unit of each requested base currency (e.g. {"USD" -> 34.12, "EUR"
     * -> 36.78}).
     */
    public Map<String, BigDecimal> fetchTryRates(Iterable<String> bases) {
        String apiKey = props.exchangeRate().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return fetchKeyless(bases);
        }

        try {
            JsonNode response =
                    webClient
                            .get()
                            .uri("/{key}/latest/TRY", apiKey)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .timeout(Duration.ofSeconds(10))
                            .block();

            if (response == null || !"success".equals(response.path("result").asText())) {
                log.warn(
                        "Exchange rate API returned non-success response, falling back to keyless"
                                + " endpoint");
                return fetchKeyless(bases);
            }

            JsonNode rates = response.path("conversion_rates");
            return extractInverse(rates, bases);
        } catch (Exception e) {
            log.warn(
                    "Exchange rate fetch failed ({}), falling back to keyless endpoint",
                    e.getMessage());
            return fetchKeyless(bases);
        }
    }

    /**
     * Keyless fallback using open.er-api.com (free, no auth). Returns TRY price of 1 unit of each
     * requested base currency.
     */
    private Map<String, BigDecimal> fetchKeyless(Iterable<String> bases) {
        try {
            JsonNode response =
                    WebClient.create("https://open.er-api.com")
                            .get()
                            .uri("/v6/latest/TRY")
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .timeout(Duration.ofSeconds(10))
                            .block();

            if (response == null || !"success".equals(response.path("result").asText())) {
                log.warn("Keyless exchange rate endpoint returned non-success response");
                return Map.of();
            }

            return extractInverse(response.path("rates"), bases);
        } catch (Exception e) {
            log.warn("Keyless exchange rate fetch failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, BigDecimal> extractInverse(JsonNode rates, Iterable<String> bases) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (String base : bases) {
            JsonNode rate = rates.path(base);
            if (rate.isNumber() && rate.decimalValue().signum() > 0) {
                BigDecimal tryPerBase =
                        BigDecimal.ONE.divide(rate.decimalValue(), 6, RoundingMode.HALF_UP);
                result.put(base, tryPerBase);
            }
        }
        return result;
    }
}
