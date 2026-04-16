package com.fintrack.audit;

import com.fintrack.audit.dto.AuditLogResponse;
import com.fintrack.common.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private static final int MAX_SIZE = 200;

    private final AuditLogRepository repository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
        Page<AuditLog> result;
        if (userId != null) {
            result = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else if (action != null && !action.isBlank()) {
            result = repository.findByActionOrderByCreatedAtDesc(action, pageable);
        } else {
            result = repository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<AuditLogResponse> items = result.getContent().stream()
                .map(AuditLogResponse::from)
                .toList();

        return ResponseEntity.ok(Map.of(
                "items", items,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }
}
