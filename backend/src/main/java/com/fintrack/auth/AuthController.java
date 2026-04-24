package com.fintrack.auth;

import com.fintrack.auth.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Authentication endpoints: register, login, refresh, logout, and profile. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Creates a new user account and returns token pair. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Authenticates user and returns token pair. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Exchanges a valid refresh token for a new token pair. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /** Revokes the provided refresh token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Returns the authenticated user's profile. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(authService.getProfile(user.getId()));
    }

    /** Completes a login flow that requires a TOTP code. */
    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponse> verifyTotp(@Valid @RequestBody TotpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyTotp(request));
    }

    /** Reports whether the current user has TOTP enabled. */
    @GetMapping("/2fa/status")
    public ResponseEntity<TotpStatusResponse> totpStatus(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(authService.totpStatus(user.getId()));
    }

    /** Starts TOTP enrolment: generates (or rotates) a secret and returns the otpauth URL. */
    @PostMapping("/2fa/setup")
    public ResponseEntity<TotpSetupResponse> totpSetup(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(authService.totpSetup(user.getId()));
    }

    /** Verifies the first code from the authenticator app and activates TOTP. */
    @PostMapping("/2fa/enable")
    public ResponseEntity<Void> totpEnable(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody TotpEnableRequest request) {
        authService.totpEnable(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    /** Disables TOTP after confirming the user's password. */
    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> totpDisable(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody TotpDisableRequest request) {
        authService.totpDisable(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    /** Updates the authenticated user's password and revokes existing refresh tokens. */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(user.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
