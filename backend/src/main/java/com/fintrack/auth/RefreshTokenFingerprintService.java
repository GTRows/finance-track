package com.fintrack.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Computes a deterministic SHA-256 fingerprint of (User-Agent + IP-prefix) for binding refresh
 * tokens to the originating session.
 */
@Service
public class RefreshTokenFingerprintService {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

    public String compute(String userAgent, String ipAddress) {
        String ua = userAgent == null ? "" : userAgent.trim();
        String prefix = ipPrefix(ipAddress);
        String input = prefix + "|" + ua;
        return sha256Hex(input);
    }

    private static String ipPrefix(String ipAddress) {
        if (ipAddress == null) return "";
        String trimmed = ipAddress.trim();
        if (trimmed.isEmpty()) return "";
        if (IPV4.matcher(trimmed).matches()) {
            int last = trimmed.lastIndexOf('.');
            return trimmed.substring(0, last) + ".0/24";
        }
        if (trimmed.contains(":")) {
            String[] groups = trimmed.split(":", -1);
            if (groups.length >= 3
                    && !groups[0].isEmpty()
                    && !groups[1].isEmpty()
                    && !groups[2].isEmpty()) {
                return groups[0] + ":" + groups[1] + ":" + groups[2] + "::/48";
            }
        }
        return trimmed;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
