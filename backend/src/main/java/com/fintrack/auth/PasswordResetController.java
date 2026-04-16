package com.fintrack.auth;

import com.fintrack.auth.dto.PasswordResetConfirmRequest;
import com.fintrack.auth.dto.PasswordResetRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /** Silent request. Always returns 200 to prevent email enumeration. */
    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody PasswordResetRequest body) {
        passwordResetService.requestReset(body.email());
        return ResponseEntity.ok(Map.of("status", "queued"));
    }

    /** Consumes the emailed token and sets a new password. */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirm(@Valid @RequestBody PasswordResetConfirmRequest body) {
        passwordResetService.confirmReset(body.token(), body.newPassword());
        return ResponseEntity.ok(Map.of("status", "reset"));
    }
}
