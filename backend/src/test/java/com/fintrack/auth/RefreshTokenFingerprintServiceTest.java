package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenFingerprintServiceTest {

    private final RefreshTokenFingerprintService service = new RefreshTokenFingerprintService();

    @Test
    void sameInputsProduceSameHash() {
        String a = service.compute("Mozilla/5.0", "1.2.3.4");
        String b = service.compute("Mozilla/5.0", "1.2.3.4");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void ipv4LastOctetIsIgnored() {
        String a = service.compute("Mozilla/5.0", "192.168.1.42");
        String b = service.compute("Mozilla/5.0", "192.168.1.99");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void ipv4DifferentThirdOctetProducesDifferentHash() {
        String a = service.compute("Mozilla/5.0", "192.168.1.42");
        String b = service.compute("Mozilla/5.0", "192.168.2.42");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void differentUserAgentsProduceDifferentHash() {
        String a = service.compute("Mozilla/5.0", "192.168.1.42");
        String b = service.compute("curl/8.0", "192.168.1.42");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void ipv6FirstThreeGroupsAreKept() {
        String a = service.compute("Mozilla/5.0", "2001:db8:abcd:1::42");
        String b = service.compute("Mozilla/5.0", "2001:db8:abcd:2::99");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void nullInputsReturnDeterministicLowerHex() {
        String fp = service.compute(null, null);
        assertThat(fp).matches("^[0-9a-f]{64}$");
        assertThat(fp).isEqualTo(service.compute(null, null));
    }

    @Test
    void blankIpAndUserAgentReturnLowerHex() {
        String fp = service.compute("Mozilla/5.0", "   ");
        assertThat(fp).matches("^[0-9a-f]{64}$");
    }

    @Test
    void whitespaceTrimmedIpv4MatchesCleanIpv4() {
        String a = service.compute("Mozilla/5.0", "   1.2.3.4   ");
        String b = service.compute("Mozilla/5.0", "1.2.3.99");
        assertThat(a).isEqualTo(b);
    }
}
