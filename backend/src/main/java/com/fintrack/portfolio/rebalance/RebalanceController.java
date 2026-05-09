package com.fintrack.portfolio.rebalance;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.portfolio.rebalance.dto.RebalanceCommitRequest;
import com.fintrack.portfolio.rebalance.dto.RebalanceCommitResult;
import com.fintrack.portfolio.rebalance.dto.RebalancePreview;
import com.fintrack.portfolio.rebalance.dto.RebalancePreviewRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rebalance preview + commit endpoints. Mounted under each portfolio because every preview is
 * scoped to a portfolio's allocation targets and live holdings.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/rebalance")
@RequiredArgsConstructor
public class RebalanceController {

    private final RebalanceService rebalanceService;

    @PostMapping("/preview")
    public ResponseEntity<RebalancePreview> preview(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody RebalancePreviewRequest request) {
        return ResponseEntity.ok(rebalanceService.preview(user.getId(), portfolioId, request));
    }

    @PostMapping("/commit")
    public ResponseEntity<RebalanceCommitResult> commit(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody RebalanceCommitRequest request) {
        return ResponseEntity.ok(rebalanceService.commit(user.getId(), portfolioId, request));
    }
}
