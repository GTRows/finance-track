package com.fintrack.auth.webauthn.dto;

import java.time.Instant;
import java.util.UUID;

/** Authenticator credential summary returned to the authenticated user. */
public record AuthenticatorResponse(
        UUID id, String name, Instant createdAt, Instant lastUsedAt, String transports) {}
