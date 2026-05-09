package com.fintrack.budget.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.common.exception.BusinessRuleException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceiptUrlSignerTest {

    private static final String VALID_SECRET = "dev-receipt-signing-secret-change-in-prod";

    private ReceiptUrlSigner signer(String secret, Duration ttl) {
        ReceiptSigningProperties props = new ReceiptSigningProperties(secret, ttl);
        ReceiptUrlSigner s = new ReceiptUrlSigner(props);
        s.validateSecret();
        return s;
    }

    private ReceiptUrlSigner defaultSigner() {
        return signer(VALID_SECRET, Duration.ofMinutes(5));
    }

    @Test
    void verifyReturnsTrueForValidToken() {
        ReceiptUrlSigner s = defaultSigner();
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        String token = s.sign(userId, txnId);

        assertThat(s.verify(userId, txnId, token)).isTrue();
    }

    @Test
    void verifyReturnsFalseForTamperedToken() {
        ReceiptUrlSigner s = defaultSigner();
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        String token = s.sign(userId, txnId);
        // Tamper the first character of the signature segment. Flipping the LAST
        // character of an unpadded base64url string is unreliable because the
        // trailing 2 bits in a 43-char encoding of 32 bytes are unused, so some
        // last-char flips decode to identical bytes.
        String tampered = flipFirstSignatureChar(token);

        assertThat(s.verify(userId, txnId, tampered)).isFalse();
    }

    private static String flipFirstSignatureChar(String token) {
        int dot = token.indexOf('.');
        int idx = dot >= 0 ? dot + 1 : 0;
        char ch = token.charAt(idx);
        char flipped = (ch == 'A') ? 'B' : 'A';
        return token.substring(0, idx) + flipped + token.substring(idx + 1);
    }

    @Test
    void verifyReturnsFalseForWrongUserId() {
        ReceiptUrlSigner s = defaultSigner();
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        String token = s.sign(userId, txnId);

        assertThat(s.verify(UUID.randomUUID(), txnId, token)).isFalse();
    }

    @Test
    void verifyReturnsFalseForWrongTxnId() {
        ReceiptUrlSigner s = defaultSigner();
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        String token = s.sign(userId, txnId);

        assertThat(s.verify(userId, UUID.randomUUID(), token)).isFalse();
    }

    @Test
    void verifyReturnsFalseForExpiredToken() throws InterruptedException {
        ReceiptUrlSigner s = signer(VALID_SECRET, Duration.ofSeconds(1));
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        String token = s.sign(userId, txnId);
        Thread.sleep(1100);

        assertThat(s.verify(userId, txnId, token)).isFalse();
    }

    @Test
    void validateSecretThrowsWhenSecretShorterThan32Bytes() {
        ReceiptSigningProperties props =
                new ReceiptSigningProperties("tooshort", Duration.ofMinutes(5));
        ReceiptUrlSigner s = new ReceiptUrlSigner(props);

        assertThatThrownBy(s::validateSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void signReturnsSameLengthForSameInputs() {
        ReceiptUrlSigner s = defaultSigner();
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        String t1 = s.sign(userId, txnId);
        String t2 = s.sign(userId, txnId);

        assertThat(t1).hasSameSizeAs(t2);
    }

    @Test
    void verifyAndExtractUserIdReturnsUserIdForValidToken() {
        ReceiptUrlSigner s = defaultSigner();
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        String token = s.sign(userId, txnId);
        UUID extracted = s.verifyAndExtractUserId(txnId, token);

        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    void verifyAndExtractUserIdThrowsForTamperedToken() {
        ReceiptUrlSigner s = defaultSigner();
        UUID userId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        String token = s.sign(userId, txnId);
        String tampered = flipFirstSignatureChar(token);

        assertThatThrownBy(() -> s.verifyAndExtractUserId(txnId, tampered))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).getCode())
                                        .isEqualTo("RECEIPT_TOKEN_INVALID"));
    }
}
