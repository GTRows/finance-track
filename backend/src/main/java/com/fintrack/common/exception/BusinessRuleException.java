package com.fintrack.common.exception;

/** Thrown when a business rule is violated (e.g., duplicate username). Maps to HTTP 400. */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String message, String code) {
        super(message);
        this.code = code;
    }

    /** Returns the machine-readable error code (SCREAMING_SNAKE_CASE). */
    public String getCode() {
        return code;
    }
}
