package com.fintrack.auth.webauthn.dto;

import java.util.List;

/** Server payload that drives the browser's navigator.credentials.get() call. */
public record AssertionStartResponse(
        String challenge,
        String rpId,
        long timeout,
        List<AllowedCredential> allowCredentials,
        String userVerification) {}
