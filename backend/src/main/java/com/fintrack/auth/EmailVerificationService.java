package com.fintrack.auth;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.EmailVerification;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.notification.MailProperties;
import com.fintrack.notification.MailService;
import com.fintrack.notification.MailTemplate;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationRepository repository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final MailProperties mailProperties;
    private final AuditService auditService;
    private final LoginRateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();

    /**
     * Issues a fresh verification token for the user and sends the email. Silent if the user is
     * already verified.
     */
    @Transactional
    public void sendVerification(UUID userId) {
        rateLimiter.enforceSensitive("email-verify-send");
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isEmailVerified()) {
            return;
        }
        repository.consumeOutstandingForUser(user.getId(), Instant.now());

        String token = generateToken();
        EmailVerification entry =
                EmailVerification.builder()
                        .userId(user.getId())
                        .token(token)
                        .expiresAt(Instant.now().plus(TOKEN_TTL))
                        .build();
        repository.save(entry);

        String link = mailProperties.getBaseUrl() + "/verify-email?token=" + token;
        String body =
                MailTemplate.wrap(
                        """
<h2 style="margin:0 0 12px;color:#f8fafc;font-size:20px">Confirm your email</h2>
<p>Welcome to FinTrack Pro, %s. Click the button below to activate your account. The link expires in 24 hours.</p>
%s
"""
                                .formatted(
                                        MailTemplate.escape(user.getUsername()),
                                        MailTemplate.actionButton(link, "Verify email")));
        mailService.sendHtml(user.getEmail(), "Verify your FinTrack Pro email", body);
        auditService.success(AuditAction.EMAIL_VERIFICATION_SENT, user.getId(), user.getUsername());
    }

    /** Consumes a token and flips the user's verified flag. */
    @Transactional
    public User confirm(String token) {
        EmailVerification entry =
                repository
                        .findByToken(token)
                        .orElseThrow(
                                () ->
                                        new BusinessRuleException(
                                                "Invalid verification link",
                                                "EMAIL_TOKEN_INVALID"));
        if (entry.getConsumedAt() != null) {
            throw new BusinessRuleException("This link has already been used", "EMAIL_TOKEN_USED");
        }
        if (entry.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Verification link expired", "EMAIL_TOKEN_EXPIRED");
        }
        User user =
                userRepository
                        .findById(entry.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        entry.setConsumedAt(Instant.now());
        repository.save(entry);

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            userRepository.save(user);
            log.info("Email verified for user {}", user.getUsername());
            auditService.success(
                    AuditAction.EMAIL_VERIFICATION_CONFIRMED, user.getId(), user.getUsername());
        }
        return user;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
