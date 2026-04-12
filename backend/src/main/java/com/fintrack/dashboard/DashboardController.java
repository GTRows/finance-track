package com.fintrack.dashboard;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.dashboard.dto.DashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardResponse> dashboard(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(dashboardService.build(user.getId()));
    }
}
