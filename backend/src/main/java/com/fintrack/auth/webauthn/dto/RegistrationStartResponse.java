package com.fintrack.auth.webauthn.dto;

import java.util.List;

/** Server payload that drives the browser's navigator.credentials.create() call. */
public record RegistrationStartResponse(
        String challenge,
        String rpId,
        String rpName,
        String userId,
        String userName,
        List<PubKeyCredParam> pubKeyCredParams,
        long timeout,
        String attestation,
        String userVerification,
        String residentKey) {}
