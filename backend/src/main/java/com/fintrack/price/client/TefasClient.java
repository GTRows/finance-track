package com.fintrack.price.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Public TEFAS price client. TEFAS exposes a per-fund history endpoint at
 * {@code /api/DB/BindHistoryInfo} that accepts form-urlencoded bodies and
 * returns a paginated list of daily prices. We request a 7 day window and take
 * the most recent non-null {@code FIYAT} as the current unit price.
 *
 * <p>There is no batch endpoint, so the caller must invoke {@link #fetchPrice}
 * once per fund code. The scheduler spaces these out itself.
 */
@Component
@Slf4j
public class TefasClient {

    private static final DateTimeFormatter TR_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int LOOKBACK_DAYS = 10;

    private final WebClient webClient;

    public TefasClient(@Qualifier("tefasWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /** Fund category as TEFAS classifies it. */
    public enum FundType {
        /** Regular investment fund. */
        YAT,
        /** Pension (BES) fund. */
        EMK
    }

    /**
     * Fetches the most recent unit price for a TEFAS fund. Returns null on any
     * failure — the scheduler should keep going regardless.
     */
    public BigDecimal fetchPrice(String fundCode, FundType type) {
        if (fundCode == null || fundCode.isBlank()) {
            return null;
        }

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(LOOKBACK_DAYS);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("fontip", type == FundType.EMK ? "EMK" : "YAT");
        form.add("sfontur", "");
        form.add("fonkod", fundCode);
        form.add("fongrup", "");
        form.add("bastarih", TR_DATE.format(start));
        form.add("bittarih", TR_DATE.format(end));
        form.add("fonturkod", "");
        form.add("fonunvantip", "");

        try {
            JsonNode response = webClient.post()
                    .uri("/api/DB/BindHistoryInfo")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || !response.has("data") || !response.get("data").isArray()) {
                log.debug("TEFAS empty response for fund={}", fundCode);
                return null;
            }

            JsonNode data = response.get("data");
            BigDecimal latestPrice = null;
            long latestTs = Long.MIN_VALUE;

            for (JsonNode row : data) {
                if (!row.has("FIYAT") || row.get("FIYAT").isNull()) {
                    continue;
                }
                BigDecimal price = row.get("FIYAT").decimalValue();
                long ts = row.has("TARIH") && row.get("TARIH").isNumber()
                        ? row.get("TARIH").longValue()
                        : 0L;
                if (ts >= latestTs) {
                    latestTs = ts;
                    latestPrice = price;
                }
            }

            if (latestPrice == null) {
                log.debug("TEFAS returned no usable rows for fund={}", fundCode);
            }
            return latestPrice;
        } catch (Exception e) {
            log.warn("TEFAS fetch failed for fund={}: {}", fundCode, e.getMessage());
            return null;
        }
    }
}
