package com.fintrack.auth;

/** Thrown when a login attempt exceeds the configured rate limit. */
public class LoginRateLimitException extends RuntimeException {
    public LoginRateLimitException(String message) {
        super(message);
    }
}
