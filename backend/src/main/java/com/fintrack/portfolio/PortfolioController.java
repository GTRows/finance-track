package com.fintrack.portfolio;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.portfolio.dto.CreatePortfolioRequest;
import com.fintrack.portfolio.dto.PortfolioResponse;
import com.fintrack.portfolio.dto.UpdatePortfolioRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing user portfolios.
 */
@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    /** Lists all portfolios owned by the authenticated user. */
    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> list(@AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(portfolioService.listForUser(user.getId()));
    }

    /** Returns a single portfolio by id. */
    @GetMapping("/{id}")
    public ResponseEntity<PortfolioResponse> get(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(portfolioService.getForUser(user.getId(), id));
    }

    /** Creates a new portfolio. */
    @PostMapping
    public ResponseEntity<PortfolioResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody CreatePortfolioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portfolioService.create(user.getId(), request));
    }

    /** Updates an existing portfolio's name and description. */
    @PutMapping("/{id}")
    public ResponseEntity<PortfolioResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePortfolioRequest request) {
        return ResponseEntity.ok(portfolioService.update(user.getId(), id, request));
    }

    /** Soft-deletes a portfolio. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        portfolioService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
