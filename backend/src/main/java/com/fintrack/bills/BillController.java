package com.fintrack.bills;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.bills.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @GetMapping
    public ResponseEntity<List<BillResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(billService.listForUser(user.getId()));
    }

    @PostMapping
    public ResponseEntity<BillResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody CreateBillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billService.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BillResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody CreateBillRequest request) {
        return ResponseEntity.ok(billService.update(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        billService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<BillResponse> pay(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody PayBillRequest request) {
        return ResponseEntity.ok(billService.pay(user.getId(), id, request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<PaymentHistoryResponse>> history(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(billService.history(user.getId(), id));
    }

    @PostMapping("/{id}/mark-used")
    public ResponseEntity<BillResponse> markUsed(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(billService.markUsed(user.getId(), id));
    }

    @GetMapping("/audit")
    public ResponseEntity<SubscriptionAuditDto> audit(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(billService.audit(user.getId()));
    }
}
