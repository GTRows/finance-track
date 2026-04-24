package com.fintrack.watchlist;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.watchlist.dto.AddWatchlistRequest;
import com.fintrack.watchlist.dto.WatchlistEntryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService service;

    @GetMapping
    public ResponseEntity<List<WatchlistEntryResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.list(user.getId()));
    }

    @PostMapping
    public ResponseEntity<WatchlistEntryResponse> add(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody AddWatchlistRequest request) {
        return ResponseEntity.ok(service.add(user.getId(), request));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID assetId) {
        service.remove(user.getId(), assetId);
        return ResponseEntity.noContent().build();
    }
}
