package com.fintrack.debt;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.debt.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService service;

    @GetMapping
    public ResponseEntity<List<DebtResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.list(user.getId()));
    }

    @PostMapping
    public ResponseEntity<DebtResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UpsertDebtRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DebtResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertDebtRequest request) {
        return ResponseEntity.ok(service.update(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        service.archive(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<DebtPaymentResponse>> payments(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.listPayments(user.getId(), id));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<DebtPaymentResponse> addPayment(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody DebtPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addPayment(user.getId(), id, request));
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    public ResponseEntity<Void> deletePayment(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @PathVariable UUID paymentId) {
        service.deletePayment(user.getId(), id, paymentId);
        return ResponseEntity.noContent().build();
    }
}
