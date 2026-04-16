package com.fintrack.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/email-verify")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /** Exchanges a verification token for a confirmation. Public. */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing_token"));
        }
        emailVerificationService.confirm(token);
        return ResponseEntity.ok(Map.of("status", "verified"));
    }

    /** Re-issues the verification email for the signed-in user. */
    @PostMapping("/resend")
    public ResponseEntity<Map<String, String>> resend(@AuthenticationPrincipal FinTrackUserDetails user) {
        emailVerificationService.sendVerification(user.getId());
        return ResponseEntity.ok(Map.of("status", "queued"));
    }
}
