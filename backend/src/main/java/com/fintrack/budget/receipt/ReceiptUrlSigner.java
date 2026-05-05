package com.fintrack.budget.receipt;

import com.fintrack.common.exception.BusinessRuleException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies short-lived HMAC-SHA256 signed tokens for receipt download URLs.
 *
 * <p>Token format (base64url-encoded): {@code userId ":" txnId ":" expiryEpochSec ":" hexMac} where
 * the MAC covers the same canonical string {@code userId|txnId|expiryEpochSec}. The userId and
 * txnId are embedded so the verifier is self-contained and stateless.
 */
@Service
@RequiredArgsConstructor
public class ReceiptUrlSigner {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    private final ReceiptSigningProperties properties;

    @PostConstruct
    void validateSecret() {
        String secret = properties.signingSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "fintrack.receipt.signing-secret must be at least 32 bytes");
        }
    }

    /** Signs a receipt URL token for the given owner and transaction. */
    public String sign(UUID userId, UUID txnId) {
        long expiresAt = Instant.now().plus(properties.tokenTtl()).getEpochSecond();
        String mac = computeMac(userId, txnId, expiresAt);
        String raw = userId + ":" + txnId + ":" + expiresAt + ":" + mac;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verifies the token and extracts the userId encoded in it.
     *
     * <p>Also validates that the txnId in the token matches the supplied txnId to prevent a signed
     * token for one transaction being reused for another.
     *
     * @throws BusinessRuleException RECEIPT_TOKEN_INVALID on any failure
     */
    public UUID verifyAndExtractUserId(UUID txnId, String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 4);
            if (parts.length != 4) {
                throw invalid();
            }
            UUID tokenUserId = UUID.fromString(parts[0]);
            UUID tokenTxnId = UUID.fromString(parts[1]);
            long expiresAt = Long.parseLong(parts[2]);
            String providedMac = parts[3];

            if (!tokenTxnId.equals(txnId)) throw invalid();
            if (Instant.ofEpochSecond(expiresAt).isBefore(Instant.now())) throw invalid();

            String expected = computeMac(tokenUserId, txnId, expiresAt);
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    providedMac.getBytes(StandardCharsets.UTF_8))) {
                throw invalid();
            }
            return tokenUserId;
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    /** Returns true iff the token is valid, unexpired, and bound to the given userId and txnId. */
    public boolean verify(UUID userId, UUID txnId, String token) {
        try {
            return verifyAndExtractUserId(txnId, token).equals(userId);
        } catch (BusinessRuleException e) {
            return false;
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException("Invalid signed URL", "RECEIPT_TOKEN_INVALID");
    }

    private String computeMac(UUID userId, UUID txnId, long expiresAt) {
        try {
            byte[] keyBytes = properties.signingSecret().getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_ALGO);
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(keySpec);
            String canonical = userId + "|" + txnId + "|" + expiresAt;
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
