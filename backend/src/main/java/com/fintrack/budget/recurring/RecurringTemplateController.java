package com.fintrack.budget.recurring;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.budget.recurring.dto.RecurringTemplateResponse;
import com.fintrack.budget.recurring.dto.UpsertRecurringRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budget/recurring")
@RequiredArgsConstructor
public class RecurringTemplateController {

    private final RecurringTemplateService service;

    @GetMapping
    public ResponseEntity<List<RecurringTemplateResponse>> list(@AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.listForUser(user.getId()));
    }

    @PostMapping
    public ResponseEntity<RecurringTemplateResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UpsertRecurringRequest request) {
        return ResponseEntity.ok(service.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringTemplateResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertRecurringRequest request) {
        return ResponseEntity.ok(service.update(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        service.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run-now")
    public ResponseEntity<RecurringTemplateResponse> runNow(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.runNow(user.getId(), id));
    }
}
