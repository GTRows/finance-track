package com.fintrack.auth;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.PasswordReset;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.notification.MailProperties;
import com.fintrack.notification.MailService;
import com.fintrack.notification.MailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 32;

    private final PasswordResetRepository repository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final MailService mailService;
    private final MailProperties mailProperties;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    /** Issues a reset link if the email is known. Always silent to callers. */
    @Transactional
    public void requestReset(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            log.debug("Password reset requested for unknown email");
            return;
        }
        User user = maybeUser.get();
        repository.consumeOutstandingForUser(user.getId(), Instant.now());

        String token = generateToken();
        PasswordReset entry = PasswordReset.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(Instant.now().plus(TOKEN_TTL))
                .build();
        repository.save(entry);

        String link = mailProperties.getBaseUrl() + "/reset-password?token=" + token;
        String body = MailTemplate.wrap("""
                <h2 style="margin:0 0 12px;color:#f8fafc;font-size:20px">Reset your password</h2>
                <p>Hi %s, use the button below to pick a new password. The link expires in one hour and can only be used once.</p>
                %s
                <p>If you did not request this reset, you can ignore this email.</p>
                """.formatted(MailTemplate.escape(user.getUsername()), MailTemplate.actionButton(link, "Reset password")));
        mailService.sendHtml(user.getEmail(), "Reset your FinTrack Pro password", body);
        auditService.success(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), user.getUsername());
    }

    /** Consumes a token, sets the new password, and revokes all existing sessions. */
    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordReset entry = repository.findByToken(token)
                .orElseThrow(() -> new BusinessRuleException("Invalid reset link", "RESET_TOKEN_INVALID"));
        if (entry.getConsumedAt() != null) {
            throw new BusinessRuleException("This link has already been used", "RESET_TOKEN_USED");
        }
        if (entry.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Reset link expired", "RESET_TOKEN_EXPIRED");
        }
        User user = userRepository.findById(entry.getUserId())
                .orElseThrow(() -> new BusinessRuleException("Invalid reset link", "RESET_TOKEN_INVALID"));

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessRuleException("New password must differ from the current one", "PASSWORD_UNCHANGED");
        }

        entry.setConsumedAt(Instant.now());
        repository.save(entry);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        log.info("Password reset completed for user {}", user.getUsername());
        auditService.success(AuditAction.PASSWORD_RESET_CONFIRMED, user.getId(), user.getUsername());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
