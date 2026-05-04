package com.fintrack.auth.webauthn.dto;

import java.util.List;

/** One entry in the allowCredentials list returned by assertion start. */
public record AllowedCredential(String type, String id, List<String> transports) {}
