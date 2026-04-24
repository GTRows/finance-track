package com.fintrack.portfolio.dividend;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.portfolio.dividend.dto.DividendResponse;
import com.fintrack.portfolio.dividend.dto.RecordDividendRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService service;

    @GetMapping("/portfolios/{portfolioId}/dividends")
    public ResponseEntity<List<DividendResponse>> listForPortfolio(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID portfolioId) {
        return ResponseEntity.ok(service.listForPortfolio(user.getId(), portfolioId));
    }

    @PostMapping("/portfolios/{portfolioId}/dividends")
    public ResponseEntity<DividendResponse> record(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody RecordDividendRequest request) {
        return ResponseEntity.ok(service.record(user.getId(), portfolioId, request));
    }

    @DeleteMapping("/portfolios/{portfolioId}/dividends/{dividendId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @PathVariable UUID dividendId) {
        service.delete(user.getId(), portfolioId, dividendId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assets/{assetId}/dividends")
    public ResponseEntity<List<DividendResponse>> listForAsset(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID assetId) {
        return ResponseEntity.ok(service.listForAsset(user.getId(), assetId));
    }
}
