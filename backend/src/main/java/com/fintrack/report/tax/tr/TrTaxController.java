package com.fintrack.report.tax.tr;

import com.fintrack.auth.FinTrackUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/tax/tr")
@RequiredArgsConstructor
public class TrTaxController {

    private static final int MIN_YEAR = 2000;

    private final TrTaxService trTaxService;

    @GetMapping
    public ResponseEntity<TrTaxReportResponse> report(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam(required = false) Integer year) {
        if (year != null && year < MIN_YEAR) {
            throw new IllegalArgumentException(
                    "year must be >= " + MIN_YEAR + " (got " + year + ")");
        }
        return ResponseEntity.ok(trTaxService.compute(user.getId(), year));
    }
}
