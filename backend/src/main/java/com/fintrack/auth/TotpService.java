package com.fintrack.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DRIFT_WINDOW = 1;

    private final String issuer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TotpService(@Value("${fintrack.totp.issuer:FinTrack Pro}") String issuer) {
        this(issuer, Clock.systemUTC());
    }

    TotpService(String issuer, Clock clock) {
        this.issuer = issuer;
        this.clock = clock;
    }

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String buildOtpauthUrl(String secret, String accountLabel) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encodedLabel = URLEncoder.encode(accountLabel, StandardCharsets.UTF_8);
        return "otpauth://totp/"
                + encodedIssuer
                + ":"
                + encodedLabel
                + "?secret="
                + secret
                + "&issuer="
                + encodedIssuer
                + "&algorithm=SHA1&digits="
                + CODE_DIGITS
                + "&period="
                + TIME_STEP_SECONDS;
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null) return false;
        String trimmed = code.replace(" ", "").trim();
        if (trimmed.length() != CODE_DIGITS) return false;
        int target;
        try {
            target = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] key = base32Decode(secret);
        long currentStep = clock.millis() / 1000L / TIME_STEP_SECONDS;
        for (int offset = -DRIFT_WINDOW; offset <= DRIFT_WINDOW; offset++) {
            if (generateCode(key, currentStep + offset) == target) {
                return true;
            }
        }
        return false;
    }

    private int generateCode(byte[] key, long step) {
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (step & 0xff);
            step >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0xf;
            int binary =
                    ((hash[offset] & 0x7f) << 24)
                            | ((hash[offset + 1] & 0xff) << 16)
                            | ((hash[offset + 2] & 0xff) << 8)
                            | (hash[offset + 3] & 0xff);
            int modulus = 1;
            for (int i = 0; i < CODE_DIGITS; i++) modulus *= 10;
            return binary % modulus;
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    private static String base32Encode(byte[] input) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : input) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1f));
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String input) {
        String cleaned = input.replace("=", "").toUpperCase();
        int outputLength = cleaned.length() * 5 / 8;
        byte[] output = new byte[outputLength];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : cleaned.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) throw new IllegalArgumentException("Invalid base32 character: " + c);
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                output[index++] = (byte) ((buffer >> bitsLeft) & 0xff);
            }
        }
        return output;
    }
}
