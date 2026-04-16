package com.fintrack.savings;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.savings.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/savings/goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalService service;

    @GetMapping
    public ResponseEntity<List<GoalResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.list(user.getId()));
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UpsertGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertGoalRequest request) {
        return ResponseEntity.ok(service.update(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        service.archive(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/contributions")
    public ResponseEntity<List<ContributionResponse>> contributions(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.listContributions(user.getId(), id));
    }

    @PostMapping("/{id}/contributions")
    public ResponseEntity<ContributionResponse> addContribution(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody ContributionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addContribution(user.getId(), id, request));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ResponseEntity<Void> deleteContribution(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @PathVariable UUID contributionId) {
        service.deleteContribution(user.getId(), id, contributionId);
        return ResponseEntity.noContent().build();
    }
}
