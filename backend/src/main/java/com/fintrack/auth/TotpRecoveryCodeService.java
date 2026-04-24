package com.fintrack.auth;

import com.fintrack.common.entity.TotpRecoveryCode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates, stores, and verifies single-use recovery codes for 2FA bypass. Codes are 10
 * alphanumeric chars formatted as {@code XXXXX-XXXXX}; stored as BCrypt hashes so a database leak
 * cannot unlock accounts without the plaintext.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TotpRecoveryCodeService {

    /** How many codes to generate per user at enrollment / regeneration. */
    public static final int CODE_COUNT = 10;

    /** Alphabet for codes (crockford base32 minus ambiguous I/L/O/U). */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";

    private final TotpRecoveryCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    /**
     * Drops any existing codes and issues a fresh batch. Returns the plaintext codes so the caller
     * can show them once.
     */
    @Transactional
    public List<String> regenerate(UUID userId) {
        repository.deleteByUserId(userId);
        List<String> plaintext = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = newCode();
            plaintext.add(code);
            repository.save(
                    TotpRecoveryCode.builder()
                            .userId(userId)
                            .codeHash(passwordEncoder.encode(code))
                            .build());
        }
        log.info("Generated {} TOTP recovery codes for user {}", CODE_COUNT, userId);
        return plaintext;
    }

    /**
     * Attempt to redeem a recovery code. Returns true on hit and marks the code as consumed;
     * returns false when no active code matches.
     */
    @Transactional
    public boolean redeem(UUID userId, String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        String normalised = normalise(candidate);
        if (normalised.length() != 11) return false;

        List<TotpRecoveryCode> active = repository.findActiveByUserId(userId);
        for (TotpRecoveryCode code : active) {
            if (passwordEncoder.matches(normalised, code.getCodeHash())) {
                code.setConsumedAt(Instant.now());
                repository.save(code);
                log.info(
                        "Recovery code redeemed for user {}. {} codes remain.",
                        userId,
                        active.size() - 1);
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public long remaining(UUID userId) {
        return repository.countByUserIdAndConsumedAtIsNull(userId);
    }

    @Transactional
    public void invalidateAll(UUID userId) {
        repository.deleteByUserId(userId);
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String normalise(String raw) {
        String trimmed = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (trimmed.length() == 10) {
            return trimmed.substring(0, 5) + "-" + trimmed.substring(5);
        }
        return trimmed;
    }
}
