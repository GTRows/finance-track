package com.fintrack.budget.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for the receipt OCR worker; bound from {@code fintrack.ocr.*}. */
@ConfigurationProperties(prefix = "fintrack.ocr")
public record OcrProperties(
        String tessdataPath, String languages, int batchSize, long pollIntervalMs, int maxRetries) {

    public OcrProperties {
        if (tessdataPath == null || tessdataPath.isBlank()) tessdataPath = "/app/tessdata";
        if (languages == null || languages.isBlank()) languages = "tur+eng";
        if (batchSize <= 0) batchSize = 25;
        if (pollIntervalMs <= 0) pollIntervalMs = 60_000L;
        if (maxRetries < 0) maxRetries = 3;
    }
}
