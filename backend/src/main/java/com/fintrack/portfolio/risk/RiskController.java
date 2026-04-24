package com.fintrack.portfolio.risk;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.portfolio.risk.dto.RiskMetricsResponse;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @GetMapping
    public ResponseEntity<RiskMetricsResponse> get(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID portfolioId,
            @RequestParam(name = "riskFreeRate", required = false) BigDecimal riskFreeRate) {
        return ResponseEntity.ok(riskService.compute(user.getId(), portfolioId, riskFreeRate));
    }
}
