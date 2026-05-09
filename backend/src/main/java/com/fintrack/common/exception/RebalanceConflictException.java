package com.fintrack.common.exception;

/**
 * Marker subclass of {@link BusinessRuleException} that the {@link GlobalExceptionHandler} maps to
 * HTTP 409 (CONFLICT) instead of the default 400. Used by the rebalance executor for stale-preview
 * and double-commit replay attempts where the request was syntactically valid but the cached
 * server-side state conflicts with what the client supplied.
 */
public class RebalanceConflictException extends BusinessRuleException {

    public RebalanceConflictException(String message, String code) {
        super(message, code);
    }
}
