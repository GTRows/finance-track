package com.fintrack.backup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.auth.FinTrackUserDetails;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final ObjectMapper objectMapper;

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal FinTrackUserDetails user) {
        BackupPayload payload = backupService.export(user.getId());
        byte[] body;
        try {
            body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize backup payload", e);
        }
        String filename = "fintrack-backup-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> restore(
            @AuthenticationPrincipal FinTrackUserDetails user, @RequestBody BackupPayload payload) {
        backupService.restore(user.getId(), payload);
        return ResponseEntity.ok(
                Map.of(
                        "status", "restored",
                        "transactions",
                                payload.transactions() != null ? payload.transactions().size() : 0,
                        "portfolios",
                                payload.portfolios() != null ? payload.portfolios().size() : 0,
                        "bills", payload.bills() != null ? payload.bills().size() : 0));
    }
}
