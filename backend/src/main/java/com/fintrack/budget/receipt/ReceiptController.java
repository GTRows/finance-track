package com.fintrack.budget.receipt;

import com.fintrack.auth.FinTrackUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budget/transactions/{id}/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptStorageService storage;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptStorageService.StoredReceipt> upload(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(storage.store(user.getId(), id, file));
    }

    @GetMapping
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) throws IOException {
        ReceiptStorageService.Loaded loaded = storage.load(user.getId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(loaded.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + id + "\"")
                .body(loaded.bytes());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id) {
        storage.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
