package com.fintrack.push;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VapidKeyManagerTest {

    private VapidKeyManager init(PushProperties props) {
        VapidKeyManager m = new VapidKeyManager(props);
        m.init();
        return m;
    }

    @Test
    void generatesNewKeyPairWhenBothMissing() {
        VapidKeyManager m = init(new PushProperties(null, null, "mailto:op@example.com"));

        KeyPair pair = m.keyPair();
        assertThat(pair.getPublic()).isInstanceOf(ECPublicKey.class);
        assertThat(pair.getPrivate()).isInstanceOf(ECPrivateKey.class);
        assertThat(m.getPublicKeyB64Url()).isNotBlank();
        assertThat(m.getPrivateKeyB64Url()).isNotBlank();

        byte[] pub = Base64.getUrlDecoder().decode(m.getPublicKeyB64Url());
        byte[] priv = Base64.getUrlDecoder().decode(m.getPrivateKeyB64Url());
        assertThat(pub).hasSize(65);
        assertThat(pub[0]).isEqualTo((byte) 0x04);
        assertThat(priv).hasSize(32);
    }

    @Test
    void generatesWhenOnlyPublicKeyConfigured() {
        VapidKeyManager m = init(new PushProperties("somepublic", null, null));

        assertThat(m.getPublicKeyB64Url()).isNotBlank();
        assertThat(m.getPrivateKeyB64Url()).isNotBlank();
    }

    @Test
    void generatesWhenKeysAreBlankStrings() {
        VapidKeyManager m = init(new PushProperties("", "   ", null));

        assertThat(m.getPublicKeyB64Url()).isNotBlank();
        assertThat(m.getPrivateKeyB64Url()).isNotBlank();
    }

    @Test
    void generatedPairRoundTripsThroughLoad() {
        VapidKeyManager first = init(new PushProperties(null, null, null));

        VapidKeyManager second = init(new PushProperties(
                first.getPublicKeyB64Url(), first.getPrivateKeyB64Url(), null));

        assertThat(second.getPublicKeyB64Url()).isEqualTo(first.getPublicKeyB64Url());
        assertThat(second.getPrivateKeyB64Url()).isEqualTo(first.getPrivateKeyB64Url());
    }

    @Test
    void loadAcceptsPaddedBase64() {
        VapidKeyManager first = init(new PushProperties(null, null, null));

        String paddedPub = first.getPublicKeyB64Url() + "==";
        String paddedPriv = first.getPrivateKeyB64Url() + "=";

        VapidKeyManager second = init(new PushProperties(paddedPub, paddedPriv, null));

        assertThat(second.getPublicKeyB64Url()).isEqualTo(first.getPublicKeyB64Url());
    }

    @Test
    void loadRejectsWrongPublicKeyLength() {
        String badPub = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[30]);
        String goodPriv = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

        VapidKeyManager m = new VapidKeyManager(new PushProperties(badPub, goodPriv, null));

        assertThatThrownBy(m::init)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("65-byte");
    }

    @Test
    void loadRejectsNonUncompressedPublicKey() {
        byte[] wrongPrefix = new byte[65];
        wrongPrefix[0] = 0x02;
        String badPub = Base64.getUrlEncoder().withoutPadding().encodeToString(wrongPrefix);
        String goodPriv = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

        VapidKeyManager m = new VapidKeyManager(new PushProperties(badPub, goodPriv, null));

        assertThatThrownBy(m::init).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadRejectsWrongPrivateKeyLength() {
        VapidKeyManager seed = init(new PushProperties(null, null, null));
        String shortPriv = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[20]);

        VapidKeyManager m = new VapidKeyManager(new PushProperties(seed.getPublicKeyB64Url(), shortPriv, null));

        assertThatThrownBy(m::init)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }
}
