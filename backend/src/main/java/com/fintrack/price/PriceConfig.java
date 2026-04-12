package com.fintrack.price;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient beans for each external price source. Kept isolated per provider
 * so per-source timeouts, headers, and base URLs can diverge freely.
 */
@Configuration
@EnableConfigurationProperties(PriceApiProperties.class)
public class PriceConfig {

    /** WebClient targeting the CoinGecko public API. */
    @Bean("coinGeckoWebClient")
    public WebClient coinGeckoWebClient(PriceApiProperties props) {
        WebClient.Builder builder = WebClient.builder().baseUrl(props.coingecko().baseUrl());
        if (props.coingecko().apiKey() != null && !props.coingecko().apiKey().isBlank()) {
            builder.defaultHeader("x-cg-demo-api-key", props.coingecko().apiKey());
        }
        return builder.build();
    }

    /** WebClient targeting exchangerate-api.com. */
    @Bean("exchangeRateWebClient")
    public WebClient exchangeRateWebClient(PriceApiProperties props) {
        return WebClient.builder().baseUrl(props.exchangeRate().baseUrl()).build();
    }

    /**
     * WebClient targeting the TEFAS public site. Needs a browser-ish User-Agent
     * and an explicit Accept/Origin or the endpoint returns HTML + a 403.
     */
    @Bean("tefasWebClient")
    public WebClient tefasWebClient(PriceApiProperties props) {
        return WebClient.builder()
                .baseUrl(props.tefas().baseUrl())
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (compatible; FinTrackPro/1.0; +https://github.com/)")
                .defaultHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .defaultHeader("Origin", props.tefas().baseUrl())
                .defaultHeader("Referer", props.tefas().baseUrl() + "/FonAnaliz.aspx")
                .build();
    }
}
