package com.fintrack.budget.receipt;

import com.fintrack.auth.FinTrackUserDetails;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.notification.MailProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/budget/transactions/{id}/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptStorageService storage;
    private final ReceiptUrlSigner signer;
    private final ReceiptSigningProperties signingProperties;
    private final MailProperties mailProperties;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptStorageService.StoredReceipt> upload(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.ok(storage.store(user.getId(), id, file));
    }

    /** Issues a short-lived signed URL that allows the receipt to be fetched without a JWT. */
    @GetMapping("/url")
    public ResponseEntity<ReceiptUrlResponse> getSignedUrl(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID id) {
        String token = signer.sign(user.getId(), id);
        String url =
                mailProperties.getBaseUrl()
                        + "/api/v1/budget/transactions/"
                        + id
                        + "/receipt?token="
                        + token;
        Instant expiresAt = Instant.now().plus(signingProperties.tokenTtl());
        return ResponseEntity.ok(new ReceiptUrlResponse(url, expiresAt));
    }

    /**
     * Downloads the receipt. Accepts either a valid JWT via {@code Authorization: Bearer} (normal
     * browser path) or a signed {@code ?token=} query parameter (for {@code <img src>} rendering).
     * The {@link SignedReceiptTokenFilter} runs before the JWT filter and installs a synthetic auth
     * when the token param is present; the cryptographic gate is enforced here.
     */
    @GetMapping
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal FinTrackUserDetails user,
            @PathVariable UUID id,
            @RequestParam(required = false) String token)
            throws IOException {
        UUID userId;
        if (token != null) {
            userId = signer.verifyAndExtractUserId(id, token);
        } else if (user != null) {
            userId = user.getId();
        } else {
            throw new BusinessRuleException("Authentication required", "RECEIPT_TOKEN_INVALID");
        }
        ReceiptStorageService.Loaded loaded = storage.load(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(loaded.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + id + "\"")
                .body(loaded.bytes());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal FinTrackUserDetails user, @PathVariable UUID id) {
        storage.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
