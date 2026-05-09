package com.fintrack.analytics.montecarlo;

import com.fintrack.analytics.montecarlo.dto.MonteCarloDefaultsResponse;
import com.fintrack.analytics.montecarlo.dto.MonteCarloRequest;
import com.fintrack.analytics.montecarlo.dto.MonteCarloResponse;
import com.fintrack.auth.FinTrackUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for the Monte Carlo net-worth projection. Service owns all orchestration. */
@RestController
@RequestMapping("/api/v1/analytics/monte-carlo")
@RequiredArgsConstructor
public class MonteCarloController {

    private final MonteCarloService service;

    /** Runs the simulation and returns the per-year percentile fan + summary stats. */
    @PostMapping
    public ResponseEntity<MonteCarloResponse> compute(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody MonteCarloRequest request) {
        return ResponseEntity.ok(service.compute(user.getId(), request));
    }

    /**
     * Returns the YAML-backed defaults so the editor pre-fills on first render without an extra
     * round trip to start configured.
     */
    @GetMapping("/defaults")
    public ResponseEntity<MonteCarloDefaultsResponse> defaults() {
        return ResponseEntity.ok(service.defaults());
    }
}
