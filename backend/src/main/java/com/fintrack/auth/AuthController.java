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

    /**
     * Verifies the first code from the authenticator app, activates TOTP, and returns the 10
     * single-use recovery codes. These codes are only shown once.
     */
    @PostMapping("/2fa/enable")
    public ResponseEntity<TotpEnableResponse> totpEnable(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody TotpEnableRequest request) {
        return ResponseEntity.ok(authService.totpEnable(user.getId(), request));
    }

    /** Disables TOTP after confirming the user's password. */
    @PostMapping("/2fa/disable")
    public ResponseEntity<Void> totpDisable(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody TotpDisableRequest request) {
        authService.totpDisable(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    /** Regenerates the recovery-code set after confirming the user's password. */
    @PostMapping("/2fa/recovery-codes/regenerate")
    public ResponseEntity<RecoveryCodesResponse> regenerateRecoveryCodes(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody TotpDisableRequest request) {
        return ResponseEntity.ok(authService.regenerateRecoveryCodes(user.getId(), request));
    }

    /** Completes a login flow by redeeming a TOTP recovery code. */
    @PostMapping("/2fa/recovery/verify")
    public ResponseEntity<AuthResponse> verifyRecoveryCode(
            @Valid @RequestBody TotpRecoveryVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyRecoveryCode(request));
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
