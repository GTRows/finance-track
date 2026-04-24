package com.fintrack.analytics;

import com.fintrack.analytics.dto.CashFlowProjectionResponse;
import com.fintrack.auth.FinTrackUserDetails;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class CashFlowProjectionController {

    private final CashFlowProjectionService service;

    @GetMapping("/cash-flow-projection")
    public ResponseEntity<CashFlowProjectionResponse> project(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam(required = false) Integer months,
            @RequestParam(required = false) BigDecimal startingBalance) {
        return ResponseEntity.ok(service.project(user.getId(), months, startingBalance));
    }
}
