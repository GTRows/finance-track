package com.fintrack.alert;

import com.fintrack.alert.dto.AlertResponse;
import com.fintrack.alert.dto.CreateAlertRequest;
import com.fintrack.auth.FinTrackUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService alertService;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(alertService.listForUser(user.getId()));
    }

    @PostMapping
    public ResponseEntity<AlertResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody CreateAlertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alertService.create(user.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID id) {
        alertService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<AlertResponse> disable(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(alertService.disable(user.getId(), id));
    }
}
