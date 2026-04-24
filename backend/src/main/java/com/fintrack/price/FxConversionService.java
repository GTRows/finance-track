package com.fintrack.price;

import com.fintrack.price.client.ExchangeRateClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Converts arbitrary currency amounts using TRY as pivot. The underlying provider
 * (exchangerate-api.com keyed, open.er-api.com keyless) returns TRY-per-unit rates; crossing two
 * currencies is just a ratio.
 *
 * <p>Callers should treat a conversion failure as non-fatal — caller code decides whether to fall
 * back to the original amount or reject the request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FxConversionService {

    private static final int SCALE = 2;
    private static final String PIVOT = "TRY";

    private final ExchangeRateClient exchangeRateClient;

    /**
     * Convert {@code amount} from {@code from} currency to {@code to} currency. Returns the amount
     * unchanged when the two currencies are the same. Throws {@link CurrencyConversionException}
     * when a rate is missing on either side.
     */
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        String src = normalize(from);
        String dst = normalize(to);
        if (src.equals(dst)) {
            return amount.setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal ratio = crossRate(src, dst);
        return amount.multiply(ratio).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Return how many units of {@code to} one unit of {@code from} is worth. Uses TRY as pivot:
     * tryPerFrom / tryPerTo.
     */
    public BigDecimal crossRate(String from, String to) {
        String src = normalize(from);
        String dst = normalize(to);
        if (src.equals(dst)) return BigDecimal.ONE;

        BigDecimal tryPerSrc = tryRateFor(src);
        BigDecimal tryPerDst = tryRateFor(dst);
        return tryPerSrc.divide(tryPerDst, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal tryRateFor(String currency) {
        if (PIVOT.equals(currency)) return BigDecimal.ONE;
        Map<String, BigDecimal> rates =
                exchangeRateClient.fetchTryRates(java.util.List.of(currency));
        BigDecimal rate = rates.get(currency);
        if (rate == null || rate.signum() <= 0) {
            log.warn("FX rate unavailable for {}", currency);
            throw new CurrencyConversionException("FX rate unavailable for " + currency);
        }
        return rate;
    }

    private String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Currency code required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public static class CurrencyConversionException extends RuntimeException {
        public CurrencyConversionException(String message) {
            super(message);
        }
    }
}
