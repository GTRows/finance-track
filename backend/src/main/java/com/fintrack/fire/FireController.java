package com.fintrack.fire;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.fire.dto.FireResponse;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fire")
@RequiredArgsConstructor
public class FireController {

    private final FireService service;

    @GetMapping
    public ResponseEntity<FireResponse> compute(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @RequestParam(required = false) BigDecimal withdrawalRate,
            @RequestParam(required = false) BigDecimal expectedReturn,
            @RequestParam(required = false) BigDecimal monthlyContribution,
            @RequestParam(required = false) BigDecimal monthlyExpense,
            @RequestParam(required = false) BigDecimal netWorth) {
        return ResponseEntity.ok(
                service.compute(
                        user.getId(),
                        withdrawalRate,
                        expectedReturn,
                        monthlyContribution,
                        monthlyExpense,
                        netWorth));
    }
}
