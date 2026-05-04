package com.fintrack.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Replaces common PII patterns in audit detail strings with sentinel tokens before they hit the
 * database or the application log. The recovery-code regex mirrors the format produced by {@code
 * com.fintrack.auth.TotpRecoveryCodeService#newCode()} (5 chars + dash + 5 chars from the alphabet
 * {@code ABCDEFGHJKMNPQRSTVWXYZ23456789}); a future format change there must trigger an update
 * here.
 */
@Component
public class AuditPiiRedactor {

    private static final Map<Pattern, String> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put(
                Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
                "[REDACTED:email]");
        PATTERNS.put(
                Pattern.compile("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"),
                "[REDACTED:jwt]");
        PATTERNS.put(
                Pattern.compile("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])"),
                "[REDACTED:ip]");
        PATTERNS.put(
                Pattern.compile(
                        "(?<![:A-Fa-f0-9])(?:[A-Fa-f0-9]{1,4}:){2,7}[A-Fa-f0-9]{1,4}(?![:A-Fa-f0-9])"),
                "[REDACTED:ip]");
        PATTERNS.put(
                Pattern.compile("(?<![A-Z2-9])[A-Z2-9]{5}-[A-Z2-9]{5}(?![A-Z2-9])"),
                "[REDACTED:recovery]");
    }

    public String redact(String detail) {
        if (detail == null || detail.isBlank()) return detail;
        String result = detail;
        for (Map.Entry<Pattern, String> entry : PATTERNS.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }
}
