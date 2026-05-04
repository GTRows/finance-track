package com.fintrack.auth.webauthn.dto;

/** Public-key credential parameter advertised during registration. */
public record PubKeyCredParam(String type, long alg) {}
