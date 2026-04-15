package com.fintrack.budget;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.budget.dto.BudgetRuleResponse;
import com.fintrack.budget.dto.CreateBudgetRuleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budget/rules")
@RequiredArgsConstructor
public class BudgetRuleController {

    private final BudgetRuleService ruleService;

    @GetMapping
    public ResponseEntity<List<BudgetRuleResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(ruleService.listForUser(user.getId()));
    }

    @PostMapping
    public ResponseEntity<BudgetRuleResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody CreateBudgetRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.create(user.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        ruleService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
