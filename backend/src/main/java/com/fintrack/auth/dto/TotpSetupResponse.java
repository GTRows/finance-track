package com.fintrack.auth.dto;

public record TotpSetupResponse(String secret, String otpauthUrl) {}
