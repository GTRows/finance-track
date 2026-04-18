package com.fintrack.settings;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.settings.dto.SettingsResponse;
import com.fintrack.settings.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<SettingsResponse> get(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(settingsService.get(user.getId()));
    }

    @PutMapping
    public ResponseEntity<SettingsResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(settingsService.update(user.getId(), request));
    }

    @PostMapping("/onboarding-complete")
    public ResponseEntity<SettingsResponse> completeOnboarding(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(settingsService.markOnboardingComplete(user.getId()));
    }
}
