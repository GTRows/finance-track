package com.fintrack.report.capitalgains;

import com.fintrack.auth.FinTrackUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/capital-gains")
@RequiredArgsConstructor
public class CapitalGainsController {

    private final CapitalGainsService capitalGainsService;

    @GetMapping
    public ResponseEntity<CapitalGainsResponse> report(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(capitalGainsService.compute(user.getId(), year));
    }
}
