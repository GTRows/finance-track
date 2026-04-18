package com.fintrack.budget.rule;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.budget.rule.dto.CategoryRuleResponse;
import com.fintrack.budget.rule.dto.UpsertCategoryRuleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budget/category-rules")
@RequiredArgsConstructor
public class TransactionCategoryRuleController {

    private final TransactionCategoryRuleService ruleService;

    @GetMapping
    public ResponseEntity<List<CategoryRuleResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(ruleService.list(user.getId()));
    }

    @PostMapping
    public ResponseEntity<CategoryRuleResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UpsertCategoryRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryRuleResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertCategoryRuleRequest request) {
        return ResponseEntity.ok(ruleService.update(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        ruleService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
