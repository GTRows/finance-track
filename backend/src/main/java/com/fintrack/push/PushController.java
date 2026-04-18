package com.fintrack.push;

import com.fintrack.auth.FinTrackUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;
    private final VapidKeyManager vapid;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> vapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapid.getPublicKeyB64Url()));
    }

    public record SubscribeRequest(
            @NotBlank String endpoint,
            @NotBlank String p256dh,
            @NotBlank String auth) {
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody SubscribeRequest request,
            HttpServletRequest http) {
        String ua = http.getHeader("User-Agent");
        var saved = pushService.subscribe(user.getId(), request.endpoint(),
                request.p256dh(), request.auth(), ua);
        return ResponseEntity.ok(Map.of("id", saved.getId(), "subscribed", true));
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UnsubscribeRequest request) {
        pushService.unsubscribe(user.getId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Integer>> test(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        int delivered = pushService.sendToUser(user.getId());
        return ResponseEntity.ok(Map.of("delivered", delivered));
    }
}
