package com.fintrack.budget;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.budget.dto.*;
import com.fintrack.common.entity.BudgetTransaction;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam String month,
            @RequestParam(required = false) BudgetTransaction.TxnType type,
            @RequestParam(required = false) UUID tagId,
            Pageable pageable) {
        return ResponseEntity.ok(budgetService.listTransactions(user.getId(), month, type, tagId, pageable));
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody CreateTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(budgetService.create(user.getId(), request));
    }

    @PutMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request) {
        return ResponseEntity.ok(budgetService.update(user.getId(), id, request));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        budgetService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public ResponseEntity<BudgetSummaryResponse> summary(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam String month) {
        return ResponseEntity.ok(budgetService.summary(user.getId(), month));
    }

    @PostMapping("/summaries/{period}/snapshot")
    public ResponseEntity<MonthlySummaryResponse> snapshot(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable String period) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(budgetService.captureSnapshot(user.getId(), period));
    }

    @GetMapping("/summaries")
    public ResponseEntity<List<MonthlySummaryResponse>> summaries(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(budgetService.listSummaries(user.getId()));
    }
}
