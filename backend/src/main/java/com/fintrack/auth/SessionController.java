package com.fintrack.auth;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.dto.SessionResponse;
import com.fintrack.common.entity.RefreshToken;
import com.fintrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    /**
     * Lists active sessions for the caller. The optional refreshToken (from the client's auth store)
     * is used to flag which row represents "this device".
     */
    @PostMapping("/list")
    public ResponseEntity<List<SessionResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestBody(required = false) Map<String, String> body) {
        String currentToken = body == null ? null : body.get("refreshToken");
        UUID currentId = currentToken == null ? null
                : refreshTokenRepository.findByToken(currentToken).map(RefreshToken::getId).orElse(null);

        List<SessionResponse> sessions = refreshTokenService.listActive(user.getId()).stream()
                .map(rt -> new SessionResponse(
                        rt.getId().toString(),
                        rt.getUserAgent(),
                        rt.getIpAddress(),
                        rt.getCreatedAt(),
                        rt.getLastUsedAt(),
                        rt.getExpiresAt(),
                        rt.getId().equals(currentId)))
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable("id") UUID id) {
        boolean revoked = refreshTokenService.revokeSession(user.getId(), id);
        if (!revoked) {
            throw new ResourceNotFoundException("Session not found");
        }
        auditService.success(AuditAction.SESSION_REVOKE, user.getId(), user.getUsername(), "session=" + id);
        return ResponseEntity.noContent().build();
    }

    /** Revokes every session except the one identified by the supplied refresh token. */
    @PostMapping("/revoke-others")
    public ResponseEntity<Map<String, Integer>> revokeOthers(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestBody Map<String, String> body) {
        String currentToken = body.get("refreshToken");
        UUID keepId = currentToken == null ? null
                : refreshTokenRepository.findByToken(currentToken).map(RefreshToken::getId).orElse(null);
        if (keepId == null) {
            throw new ResourceNotFoundException("Current session not found");
        }
        int removed = refreshTokenService.revokeOthers(user.getId(), keepId);
        auditService.success(AuditAction.SESSION_REVOKE_OTHERS, user.getId(), user.getUsername(), "count=" + removed);
        return ResponseEntity.ok(Map.of("revoked", removed));
    }
}
