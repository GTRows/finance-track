package com.fintrack.auth.webauthn;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.auth.webauthn.dto.RegistrationFinishRequest;
import com.fintrack.auth.webauthn.dto.RegistrationFinishResponse;
import com.fintrack.auth.webauthn.dto.RegistrationStartResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WebAuthn registration ceremony endpoints (authenticated user enrols a passkey). */
@RestController
@RequestMapping("/api/v1/auth/webauthn/register")
@RequiredArgsConstructor
public class WebAuthnRegistrationController {

    private final WebAuthnRegistrationService service;

    /** Returns the publicKey options for navigator.credentials.create(). */
    @PostMapping("/start")
    public ResponseEntity<RegistrationStartResponse> start(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.start(user.getId()));
    }

    /** Finalizes a registration ceremony and persists the credential. */
    @PostMapping("/finish")
    public ResponseEntity<RegistrationFinishResponse> finish(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody RegistrationFinishRequest body) {
        return ResponseEntity.ok(service.finish(user.getId(), body));
    }
}
