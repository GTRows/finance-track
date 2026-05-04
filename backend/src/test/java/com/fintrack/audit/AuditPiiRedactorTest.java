package com.fintrack.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditPiiRedactorTest {

    private final AuditPiiRedactor redactor = new AuditPiiRedactor();

    @Test
    void redactsPlainEmail() {
        assertThat(redactor.redact("user is alice@example.com"))
                .isEqualTo("user is [REDACTED:email]");
    }

    @Test
    void redactsEmailSurroundedByPunctuation() {
        assertThat(redactor.redact("contact: \"bob.smith+tag@mail.co.uk\"."))
                .isEqualTo("contact: \"[REDACTED:email]\".");
    }

    @Test
    void redactsTwoEmailsInOneString() {
        assertThat(redactor.redact("from a@b.com to c@d.org"))
                .isEqualTo("from [REDACTED:email] to [REDACTED:email]");
    }

    @Test
    void redactsJwtInBearerContext() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signaturepart-_xyz";
        assertThat(redactor.redact("Authorization: Bearer " + jwt))
                .isEqualTo("Authorization: Bearer [REDACTED:jwt]");
    }

    @Test
    void redactsPlainIpv4() {
        assertThat(redactor.redact("client 192.168.1.5 logged in"))
                .isEqualTo("client [REDACTED:ip] logged in");
    }

    @Test
    void redactsIpv4WithPortSuffix() {
        assertThat(redactor.redact("conn 10.0.0.1:8080")).isEqualTo("conn [REDACTED:ip]:8080");
    }

    @Test
    void redactsIpv4InsideUrlQueryString() {
        assertThat(redactor.redact("origin=https://api/?host=203.0.113.7&x=1"))
                .isEqualTo("origin=https://api/?host=[REDACTED:ip]&x=1");
    }

    @Test
    void redactsIpv6FullForm() {
        assertThat(redactor.redact("addr fe80:0:0:0:1:2:3:4 done"))
                .isEqualTo("addr [REDACTED:ip] done");
    }

    @Test
    void redactsIpv6CompactForm() {
        assertThat(redactor.redact("from 2001:db8:1:2:3:4:5:6")).isEqualTo("from [REDACTED:ip]");
    }

    @Test
    void redactsRecoveryCodePlain() {
        assertThat(redactor.redact("code ABCDE-FGHJK now"))
                .isEqualTo("code [REDACTED:recovery] now");
    }

    @Test
    void redactsRecoveryCodeEmbeddedInSentence() {
        assertThat(redactor.redact("Used XYZ23-456PQ for recovery"))
                .isEqualTo("Used [REDACTED:recovery] for recovery");
    }

    @Test
    void redactsMixedEmailIpAndJwtInSingleDetail() {
        String input = "user test@example.com from 192.168.1.5 token" + " eyJabc.eyJxyz.signature";
        assertThat(redactor.redact(input))
                .isEqualTo("user [REDACTED:email] from [REDACTED:ip] token [REDACTED:jwt]");
    }

    @Test
    void nullInputReturnsNull() {
        assertThat(redactor.redact(null)).isNull();
    }

    @Test
    void blankInputReturnsBlank() {
        assertThat(redactor.redact("")).isEqualTo("");
        assertThat(redactor.redact("   ")).isEqualTo("   ");
    }

    @Test
    void inputWithNoPiiIsUnchanged() {
        String input = "WEBAUTHN_LOGIN succeeded for user-id 12345";
        assertThat(redactor.redact(input)).isEqualTo(input);
    }
}
