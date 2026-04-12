package com.fintrack.portfolio.snapshot;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.portfolio.snapshot.dto.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read endpoints for portfolio history. Writes happen via {@link SnapshotScheduler}.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/history")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;

    /** Returns the chronological value history for a portfolio owned by the user. */
    @GetMapping
    public ResponseEntity<List<SnapshotResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId) {
        return ResponseEntity.ok(snapshotService.listForPortfolio(user.getId(), portfolioId));
    }
}
