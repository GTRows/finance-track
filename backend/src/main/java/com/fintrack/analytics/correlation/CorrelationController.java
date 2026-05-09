package com.fintrack.analytics.correlation;

import com.fintrack.analytics.correlation.dto.CorrelationMatrixResponse;
import com.fintrack.auth.FinTrackUserDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoint for the asset correlation matrix view. Sits under the analytics group so OpenAPI
 * stays cohesive with {@link com.fintrack.analytics.compare.PortfolioComparisonController}.
 */
@RestController
@RequestMapping("/api/v1/analytics/correlations")
@RequiredArgsConstructor
public class CorrelationController {

    private final CorrelationService service;

    /**
     * Returns a square correlation matrix for the requested asset ids over the (optional) date
     * range. Method defaults to PEARSON; SPEARMAN is also accepted (case-insensitive). Range
     * defaults to the last 90 days when both {@code from} and {@code to} are omitted.
     */
    @GetMapping
    public ResponseEntity<CorrelationMatrixResponse> compute(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam("assetIds") List<UUID> assetIds,
            @RequestParam(value = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(value = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(value = "method", required = false, defaultValue = "PEARSON")
                    CorrelationMethod method) {
        return ResponseEntity.ok(service.compute(user.getId(), assetIds, from, to, method));
    }
}
