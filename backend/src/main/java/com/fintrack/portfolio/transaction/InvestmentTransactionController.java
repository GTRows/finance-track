package com.fintrack.portfolio.transaction;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.portfolio.transaction.dto.RecordTransactionRequest;
import com.fintrack.portfolio.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/transactions")
@RequiredArgsConstructor
public class InvestmentTransactionController {

    private final InvestmentTransactionService transactionService;

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID portfolioId) {
        return ResponseEntity.ok(transactionService.list(user.getId(), portfolioId));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> record(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody RecordTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.record(user.getId(), portfolioId, request));
    }

    @DeleteMapping("/{txnId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @PathVariable UUID txnId) {
        transactionService.delete(user.getId(), portfolioId, txnId);
        return ResponseEntity.noContent().build();
    }
}
