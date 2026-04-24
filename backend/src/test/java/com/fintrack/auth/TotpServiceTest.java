package com.fintrack.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private static final String RFC6238_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private static TotpService serviceAt(long epochSeconds) {
        Clock fixed = Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
        return new TotpService("FinTrack Pro", fixed);
    }

    @Test
    void verifiesRfc6238VectorAt59Seconds() {
        TotpService service = serviceAt(59L);
        assertTrue(service.verify(RFC6238_SECRET_BASE32, "287082"));
    }

    @Test
    void verifiesRfc6238VectorAt1111111109Seconds() {
        TotpService service = serviceAt(1111111109L);
        assertTrue(service.verify(RFC6238_SECRET_BASE32, "081804"));
    }

    @Test
    void acceptsCodeFromPreviousStepWithinDriftWindow() {
        TotpService service = serviceAt(59L + 30L);
        assertTrue(service.verify(RFC6238_SECRET_BASE32, "287082"));
    }

    @Test
    void rejectsCodeOutsideDriftWindow() {
        TotpService service = serviceAt(59L + 120L);
        assertFalse(service.verify(RFC6238_SECRET_BASE32, "287082"));
    }

    @Test
    void rejectsMalformedInput() {
        TotpService service = serviceAt(59L);
        assertFalse(service.verify(RFC6238_SECRET_BASE32, null));
        assertFalse(service.verify(RFC6238_SECRET_BASE32, ""));
        assertFalse(service.verify(RFC6238_SECRET_BASE32, "12345"));
        assertFalse(service.verify(RFC6238_SECRET_BASE32, "1234567"));
        assertFalse(service.verify(RFC6238_SECRET_BASE32, "abcdef"));
        assertFalse(service.verify(null, "287082"));
    }

    @Test
    void generateSecretProducesDecodableBase32() {
        TotpService service = serviceAt(0L);
        String secret = service.generateSecret();
        assertEquals(32, secret.length());
        for (char c : secret.toCharArray()) {
            assertTrue(
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c) >= 0,
                    "secret contains non-base32 character: " + c);
        }
    }

    @Test
    void otpauthUrlContainsExpectedParameters() {
        TotpService service = serviceAt(0L);
        String url = service.buildOtpauthUrl("JBSWY3DPEHPK3PXP", "ali@example.com");
        assertTrue(url.startsWith("otpauth://totp/"));
        assertTrue(url.contains("secret=JBSWY3DPEHPK3PXP"));
        assertTrue(url.contains("issuer=FinTrack+Pro"));
        assertTrue(url.contains("algorithm=SHA1"));
        assertTrue(url.contains("digits=6"));
        assertTrue(url.contains("period=30"));
        assertTrue(url.contains("ali%40example.com"));
    }
}
