package com.fintrack.portfolio.holding;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.portfolio.holding.dto.AddHoldingRequest;
import com.fintrack.portfolio.holding.dto.HoldingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing the holdings inside a portfolio.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    /** Lists all holdings inside a portfolio. */
    @GetMapping
    public ResponseEntity<List<HoldingResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId) {
        return ResponseEntity.ok(holdingService.listForPortfolio(user.getId(), portfolioId));
    }

    /** Adds a new holding to the portfolio. */
    @PostMapping
    public ResponseEntity<HoldingResponse> add(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody AddHoldingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(holdingService.add(user.getId(), portfolioId, request));
    }

    /** Removes a holding from the portfolio. */
    @DeleteMapping("/{holdingId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @PathVariable UUID holdingId) {
        holdingService.delete(user.getId(), portfolioId, holdingId);
        return ResponseEntity.noContent().build();
    }

    /** Toggles the pinned flag on a holding (favourite at top of list). */
    @PutMapping("/{holdingId}/pin")
    public ResponseEntity<HoldingResponse> togglePin(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @PathVariable UUID holdingId) {
        return ResponseEntity.ok(holdingService.togglePin(user.getId(), portfolioId, holdingId));
    }
}
