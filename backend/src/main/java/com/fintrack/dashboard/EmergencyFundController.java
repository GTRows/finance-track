package com.fintrack.dashboard;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.dashboard.dto.EmergencyFundResponse;
import com.fintrack.dashboard.dto.UpdateEmergencyFundTypesRequest;
import com.fintrack.settings.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard endpoints for the emergency-fund tile (Phase 27 sub-plan 03 / G2-b). */
@RestController
@RequestMapping("/api/v1/dashboard/emergency-fund")
@RequiredArgsConstructor
public class EmergencyFundController {

    private final EmergencyFundService emergencyFundService;
    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<EmergencyFundResponse> get(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(emergencyFundService.compute(user.getId()));
    }

    @PutMapping("/types")
    public ResponseEntity<EmergencyFundResponse> updateTypes(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UpdateEmergencyFundTypesRequest request) {
        settingsService.updateEmergencyFundTypes(user.getId(), request.types());
        return ResponseEntity.ok(emergencyFundService.compute(user.getId()));
    }
}
