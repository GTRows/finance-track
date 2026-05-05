package com.fintrack.budget.receipt;

import java.time.Instant;

/** Response body for the signed receipt URL endpoint. */
public record ReceiptUrlResponse(String url, Instant expiresAt) {}
