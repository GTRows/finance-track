package com.fintrack.tag;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.tag.dto.TagResponse;
import com.fintrack.tag.dto.UpsertTagRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService service;

    @GetMapping
    public ResponseEntity<List<TagResponse>> list(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(service.list(user.getId()));
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @Valid @RequestBody UpsertTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> update(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertTagRequest request) {
        return ResponseEntity.ok(service.update(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        service.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
