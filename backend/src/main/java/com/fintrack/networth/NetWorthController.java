package com.fintrack.networth;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.networth.dto.NetWorthEventResponse;
import com.fintrack.networth.dto.NetWorthTimelineResponse;
import com.fintrack.networth.dto.UpsertEventRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/net-worth")
@RequiredArgsConstructor
public class NetWorthController {

    private final NetWorthService service;

    @GetMapping
    public ResponseEntity<NetWorthTimelineResponse> timeline(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.timeline(user.getId()));
    }

    @GetMapping("/events")
    public ResponseEntity<List<NetWorthEventResponse>> listEvents(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.listEvents(user.getId()));
    }

    @PostMapping("/events")
    public ResponseEntity<NetWorthEventResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UpsertEventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.getId(), req));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<NetWorthEventResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertEventRequest req) {
        return ResponseEntity.ok(service.update(user.getId(), id, req));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID id) {
        service.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
