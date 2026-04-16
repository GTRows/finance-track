package com.fintrack.notification;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.FinTrackUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/mail")
@RequiredArgsConstructor
public class MailAdminController {

    private final MailService mailService;
    private final AuditService auditService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "enabled", mailService.isEnabled(),
                "baseUrl", mailService.baseUrl()
        ));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> sendTest(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestBody Map<String, String> body) {
        String to = body.getOrDefault("to", user.getUsername());
        mailService.sendHtml(to, "FinTrack Pro SMTP test", MailTemplate.testMessage(to));
        auditService.success("MAIL_TEST", user.getId(), user.getUsername(), "to=" + to);
        if (!mailService.isEnabled()) {
            return ResponseEntity.ok(Map.of("status", "disabled", "message", "SMTP not configured; see application.yml"));
        }
        return ResponseEntity.ok(Map.of("status", "queued", "to", to));
    }
}
