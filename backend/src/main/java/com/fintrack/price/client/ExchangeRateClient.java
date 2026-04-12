package com.fintrack.price.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.price.PriceApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Exchange rate client backed by exchangerate-api.com. Returns TRY rates for
 * a small set of base currencies. Requires an API key in configuration.
 */
@Component
@Slf4j
public class ExchangeRateClient {

    private final WebClient webClient;
    private final PriceApiProperties props;

    public ExchangeRateClient(
            @Qualifier("exchangeRateWebClient") WebClient webClient,
            PriceApiProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    /**
     * Returns the TRY price of 1 unit of each requested base currency
     * (e.g. {"USD" -> 34.12, "EUR" -> 36.78}).
     */
    public Map<String, BigDecimal> fetchTryRates(Iterable<String> bases) {
        String apiKey = props.exchangeRate().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Exchange rate API key missing -- skipping fetch");
            return Map.of();
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/{key}/latest/TRY", apiKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || !"success".equals(response.path("result").asText())) {
                log.warn("Exchange rate API returned non-success response");
                return Map.of();
            }

            JsonNode rates = response.path("conversion_rates");
            Map<String, BigDecimal> result = new HashMap<>();
            for (String base : bases) {
                JsonNode rate = rates.path(base);
                if (rate.isNumber() && rate.decimalValue().signum() > 0) {
                    // Returned rate is TRY -> base, we want the inverse (price of 1 base in TRY).
                    BigDecimal tryPerBase = BigDecimal.ONE.divide(rate.decimalValue(), 6, RoundingMode.HALF_UP);
                    result.put(base, tryPerBase);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Exchange rate fetch failed: {}", e.getMessage());
            return Map.of();
        }
    }
}
