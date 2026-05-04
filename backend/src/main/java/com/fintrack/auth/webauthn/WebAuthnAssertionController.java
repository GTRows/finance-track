package com.fintrack.auth.webauthn;

import com.fintrack.auth.dto.AuthResponse;
import com.fintrack.auth.webauthn.dto.AssertionFinishRequest;
import com.fintrack.auth.webauthn.dto.AssertionStartRequest;
import com.fintrack.auth.webauthn.dto.AssertionStartResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WebAuthn assertion (login) ceremony endpoints — public, no auth required. */
@RestController
@RequestMapping("/api/v1/auth/webauthn/login")
@RequiredArgsConstructor
public class WebAuthnAssertionController {

    private final WebAuthnAssertionService service;

    /** Returns the publicKey options for navigator.credentials.get(). */
    @PostMapping("/start")
    public ResponseEntity<AssertionStartResponse> start(
            @Valid @RequestBody AssertionStartRequest body) {
        return ResponseEntity.ok(service.start(body.username()));
    }

    /** Finalizes an assertion ceremony and returns a JWT pair (or TOTP challenge). */
    @PostMapping("/finish")
    public ResponseEntity<AuthResponse> finish(@Valid @RequestBody AssertionFinishRequest body) {
        return ResponseEntity.ok(service.finish(body));
    }
}
