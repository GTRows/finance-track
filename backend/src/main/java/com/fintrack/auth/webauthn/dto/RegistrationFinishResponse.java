package com.fintrack.auth.webauthn.dto;

import java.util.UUID;

/** Confirmation payload returned after a successful registration ceremony. */
public record RegistrationFinishResponse(UUID id, String name) {}
