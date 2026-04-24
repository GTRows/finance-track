package com.fintrack.price;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code price-api.*} configuration block. */
@ConfigurationProperties(prefix = "price-api")
public record PriceApiProperties(
        CoinGecko coingecko, ExchangeRate exchangeRate, Tefas tefas, int syncIntervalSeconds) {

    public record CoinGecko(String baseUrl, String apiKey, boolean enabled) {}

    public record ExchangeRate(String baseUrl, String apiKey, boolean enabled) {}

    public record Tefas(String baseUrl, boolean enabled) {}
}
