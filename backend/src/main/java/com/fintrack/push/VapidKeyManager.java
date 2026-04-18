package com.fintrack.push;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Base64;

/**
 * Loads or generates the VAPID P-256 keypair used to sign push requests.
 * Keys are exchanged with the frontend and push providers in URL-safe base64
 * (no padding) per RFC 8291. A missing config value is not an error — the
 * manager generates a new pair and logs it so the operator can persist it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VapidKeyManager {

    private static final String EC_ALGO = "EC";
    private static final String CURVE = "secp256r1";

    private final PushProperties props;

    private KeyPair keyPair;

    @Getter
    private String publicKeyB64Url;

    @Getter
    private String privateKeyB64Url;

    @PostConstruct
    void init() {
        try {
            if (props.vapidPublicKey() == null || props.vapidPublicKey().isBlank()
                    || props.vapidPrivateKey() == null || props.vapidPrivateKey().isBlank()) {
                generate();
                log.warn("No VAPID keys configured; generated new pair. Persist these in .env to keep "
                        + "existing subscriptions valid across restarts:");
                log.warn("  PUSH_VAPID_PUBLIC_KEY={}", publicKeyB64Url);
                log.warn("  PUSH_VAPID_PRIVATE_KEY={}", privateKeyB64Url);
            } else {
                load(props.vapidPublicKey(), props.vapidPrivateKey());
                log.info("VAPID keys loaded from configuration");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize VAPID keys", e);
        }
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    private void generate() throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance(EC_ALGO);
        gen.initialize(new ECGenParameterSpec(CURVE));
        keyPair = gen.generateKeyPair();
        publicKeyB64Url = encodePublicKey((ECPublicKey) keyPair.getPublic());
        privateKeyB64Url = encodePrivateKey((ECPrivateKey) keyPair.getPrivate());
    }

    private void load(String publicB64, String privateB64) throws GeneralSecurityException {
        byte[] pub = Base64.getUrlDecoder().decode(stripPadding(publicB64));
        byte[] priv = Base64.getUrlDecoder().decode(stripPadding(privateB64));

        if (pub.length != 65 || pub[0] != 0x04) {
            throw new IllegalArgumentException("VAPID public key must be 65-byte uncompressed point");
        }
        if (priv.length != 32) {
            throw new IllegalArgumentException("VAPID private key must be 32 bytes");
        }

        KeyFactory kf = KeyFactory.getInstance(EC_ALGO);
        AlgorithmParameters params = AlgorithmParameters.getInstance(EC_ALGO);
        params.init(new ECGenParameterSpec(CURVE));
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

        BigInteger x = new BigInteger(1, slice(pub, 1, 33));
        BigInteger y = new BigInteger(1, slice(pub, 33, 65));
        ECPublicKey publicKey = (ECPublicKey) kf.generatePublic(
                new ECPublicKeySpec(new ECPoint(x, y), ecSpec));

        BigInteger s = new BigInteger(1, priv);
        ECPrivateKey privateKey = (ECPrivateKey) kf.generatePrivate(
                new ECPrivateKeySpec(s, ecSpec));

        keyPair = new KeyPair(publicKey, privateKey);
        publicKeyB64Url = encodePublicKey(publicKey);
        privateKeyB64Url = encodePrivateKey(privateKey);
    }

    private static String encodePublicKey(ECPublicKey key) {
        ECPoint point = key.getW();
        byte[] x = unsignedBytes(point.getAffineX(), 32);
        byte[] y = unsignedBytes(point.getAffineY(), 32);
        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        System.arraycopy(x, 0, uncompressed, 1, 32);
        System.arraycopy(y, 0, uncompressed, 33, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
    }

    private static String encodePrivateKey(ECPrivateKey key) {
        byte[] s = unsignedBytes(key.getS(), 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s);
    }

    private static byte[] unsignedBytes(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) return raw;
        byte[] padded = new byte[length];
        if (raw.length < length) {
            System.arraycopy(raw, 0, padded, length - raw.length, raw.length);
        } else {
            System.arraycopy(raw, raw.length - length, padded, 0, length);
        }
        return padded;
    }

    private static byte[] slice(byte[] src, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(src, from, out, 0, out.length);
        return out;
    }

    private static String stripPadding(String b64) {
        int idx = b64.indexOf('=');
        return idx < 0 ? b64 : b64.substring(0, idx);
    }
}
