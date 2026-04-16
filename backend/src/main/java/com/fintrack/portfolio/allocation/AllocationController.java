package com.fintrack.portfolio.allocation;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.portfolio.allocation.dto.AllocationSummary;
import com.fintrack.portfolio.allocation.dto.SetAllocationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/allocation")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationService service;

    @GetMapping
    public ResponseEntity<AllocationSummary> get(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId) {
        return ResponseEntity.ok(service.summarize(user.getId(), portfolioId));
    }

    @PutMapping
    public ResponseEntity<AllocationSummary> set(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody SetAllocationRequest request) {
        return ResponseEntity.ok(service.replaceTargets(user.getId(), portfolioId, request));
    }
}
