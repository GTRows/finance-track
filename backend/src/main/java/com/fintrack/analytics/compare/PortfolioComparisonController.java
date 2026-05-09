package com.fintrack.analytics.compare;

import com.fintrack.analytics.compare.dto.PortfolioComparisonResponse;
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
 * Read endpoint for the multi-portfolio comparison view. Sits under the analytics group so OpenAPI
 * stays cohesive with {@link com.fintrack.analytics.benchmark.BenchmarkController}.
 */
@RestController
@RequestMapping("/api/v1/analytics/portfolios/compare")
@RequiredArgsConstructor
public class PortfolioComparisonController {

    private final PortfolioComparisonService service;

    /**
     * Returns per-portfolio comparison series for the requested ids over the (optional) date range.
     * The response is TRY-denominated.
     */
    @GetMapping
    public ResponseEntity<PortfolioComparisonResponse> compare(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam("ids") List<UUID> ids,
            @RequestParam(value = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(value = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return ResponseEntity.ok(service.compare(user.getId(), ids, from, to));
    }
}
