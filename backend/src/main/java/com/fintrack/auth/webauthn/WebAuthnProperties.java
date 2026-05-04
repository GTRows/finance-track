package com.fintrack.auth.webauthn;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Relying-party configuration for WebAuthn ceremonies. */
@ConfigurationProperties(prefix = "fintrack.webauthn")
public record WebAuthnProperties(String rpId, String rpName, String origin) {}
