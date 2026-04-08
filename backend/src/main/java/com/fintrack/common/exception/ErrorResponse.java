package com.fintrack.common.exception;

import java.time.Instant;

/**
 * Consistent API error response format returned by all error handlers.
 */
public record ErrorResponse(
        String error,
        String code,
        String requestId,
        Instant timestamp,
        String path
) {
    /** Convenience factory with current timestamp. */
    public static ErrorResponse of(String error, String code, String requestId, String path) {
        return new ErrorResponse(error, code, requestId, Instant.now(), path);
    }
}
