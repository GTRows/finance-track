package com.fintrack.auth.dto;

import java.util.List;

/**
 * Returned after the first TOTP code is verified. The {@code recoveryCodes} list is the one and
 * only chance to copy the codes down — the server keeps only BCrypt hashes from this point on.
 */
public record TotpEnableResponse(List<String> recoveryCodes) {}
