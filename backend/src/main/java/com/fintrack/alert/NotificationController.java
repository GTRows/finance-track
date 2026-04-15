package com.fintrack.alert;

import com.fintrack.alert.dto.NotificationResponse;
import com.fintrack.alert.dto.UnreadCountResponse;
import com.fintrack.auth.FinTrackUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(notificationService.list(user.getId()));
    }

    @GetMapping("/unread")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.unreadCount(user.getId())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markAsRead(user.getId(), id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.noContent().build();
    }
}
