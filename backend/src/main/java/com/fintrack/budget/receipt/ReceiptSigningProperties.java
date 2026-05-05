package com.fintrack.budget.receipt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for HMAC-SHA256 signed receipt URLs; bound from {@code fintrack.receipt.*}. */
@ConfigurationProperties(prefix = "fintrack.receipt")
public record ReceiptSigningProperties(String signingSecret, Duration tokenTtl) {

    public ReceiptSigningProperties {
        if (tokenTtl == null) tokenTtl = Duration.ofMinutes(5);
    }
}
