package com.fintrack.auth.webauthn;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.auth.webauthn.dto.AuthenticatorResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated endpoints for listing and revoking the current user's passkey credentials. */
@RestController
@RequestMapping("/api/v1/auth/webauthn/authenticators")
@RequiredArgsConstructor
public class AuthenticatorController {

    private final AuthenticatorRepository authenticatorRepository;
    private final AuditService auditService;

    /** Returns all passkeys enrolled by the authenticated user. */
    @GetMapping
    public ResponseEntity<List<AuthenticatorResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        List<AuthenticatorResponse> result =
                authenticatorRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                        .map(
                                a ->
                                        new AuthenticatorResponse(
                                                a.getId(),
                                                a.getName(),
                                                a.getCreatedAt(),
                                                a.getLastUsedAt(),
                                                a.getTransports()))
                        .toList();
        return ResponseEntity.ok(result);
    }

    /** Revokes a specific passkey owned by the authenticated user. Returns 204 on success. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID id) {
        authenticatorRepository.deleteByIdAndUserId(id, user.getId());
        auditService.success(
                AuditAction.WEBAUTHN_AUTHENTICATOR_REVOKED,
                user.getId(),
                user.getUsername(),
                "id=" + id);
        return ResponseEntity.noContent().build();
    }
}
