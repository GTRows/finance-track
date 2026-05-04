package com.fintrack.auth.webauthn;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.UserRepository;
import com.fintrack.auth.webauthn.dto.PubKeyCredParam;
import com.fintrack.auth.webauthn.dto.RegistrationFinishRequest;
import com.fintrack.auth.webauthn.dto.RegistrationFinishResponse;
import com.fintrack.auth.webauthn.dto.RegistrationStartResponse;
import com.fintrack.common.entity.Authenticator;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.Base64UrlUtil;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Drives the WebAuthn registration ceremony (start + finish). */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebAuthnRegistrationService {

    static final String CHALLENGE_KEY_PREFIX = "webauthn:reg:";
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    static final long REGISTRATION_TIMEOUT_MS = 60_000L;
    static final int CHALLENGE_BYTES = 32;
    static final long ALG_ES256 = -7L;
    static final long ALG_RS256 = -257L;

    private final UserRepository userRepository;
    private final AuthenticatorRepository authenticatorRepository;
    private final StringRedisTemplate redis;
    private final WebAuthnManager webAuthnManager;
    private final WebAuthnProperties properties;
    private final ObjectConverter objectConverter;
    private final AuditService auditService;

    /** Issues a fresh challenge and returns the navigator.credentials.create() options. */
    @Transactional
    public RegistrationStartResponse start(UUID userId) {
        User user = loadUser(userId);

        byte[] challenge = new byte[CHALLENGE_BYTES];
        new SecureRandom().nextBytes(challenge);
        String encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);

        redis.opsForValue().set(challengeKey(userId), encodedChallenge, CHALLENGE_TTL);

        auditService.success(AuditAction.WEBAUTHN_REGISTER_STARTED, userId, user.getUsername());

        return new RegistrationStartResponse(
                encodedChallenge,
                properties.rpId(),
                properties.rpName(),
                encodeUserHandle(user.getId()),
                user.getUsername(),
                List.of(
                        new PubKeyCredParam("public-key", ALG_ES256),
                        new PubKeyCredParam("public-key", ALG_RS256)),
                REGISTRATION_TIMEOUT_MS,
                "none",
                "preferred",
                "preferred");
    }

    /** Validates the browser's attestation response and stores the credential. */
    @Transactional
    public RegistrationFinishResponse finish(UUID userId, RegistrationFinishRequest body) {
        User user = loadUser(userId);

        String encodedChallenge = redis.opsForValue().getAndDelete(challengeKey(userId));
        if (encodedChallenge == null) {
            auditService.failure(
                    AuditAction.WEBAUTHN_REGISTER_FAILED,
                    userId,
                    user.getUsername(),
                    "missing or expired challenge");
            throw new BusinessRuleException(
                    "Registration challenge invalid or expired", "WEBAUTHN_CHALLENGE_INVALID");
        }

        byte[] attestationObjectBytes = Base64UrlUtil.decode(body.attestationObject());
        byte[] clientDataJsonBytes = Base64UrlUtil.decode(body.clientDataJSON());

        ServerProperty serverProperty =
                new ServerProperty(
                        new Origin(properties.origin()),
                        properties.rpId(),
                        new DefaultChallenge(Base64UrlUtil.decode(encodedChallenge)),
                        null);
        RegistrationRequest registrationRequest =
                new RegistrationRequest(attestationObjectBytes, clientDataJsonBytes);
        RegistrationParameters parameters =
                new RegistrationParameters(serverProperty, null, false, true);

        RegistrationData data;
        try {
            data = webAuthnManager.parse(registrationRequest);
            webAuthnManager.verify(data, parameters);
        } catch (Exception ex) {
            log.warn("WebAuthn registration validation failed: {}", ex.getMessage());
            auditService.failure(
                    AuditAction.WEBAUTHN_REGISTER_FAILED,
                    userId,
                    user.getUsername(),
                    "attestation invalid");
            throw new BusinessRuleException("Registration failed", "WEBAUTHN_REGISTRATION_INVALID");
        }

        AttestedCredentialData attestedCredentialData =
                data.getAttestationObject().getAuthenticatorData().getAttestedCredentialData();
        byte[] credentialId = attestedCredentialData.getCredentialId();
        UUID aaguidValue = extractAaguid(attestedCredentialData.getAaguid());
        byte[] publicKeyCose =
                objectConverter
                        .getCborConverter()
                        .writeValueAsBytes(attestedCredentialData.getCOSEKey());
        long signCount = data.getAttestationObject().getAuthenticatorData().getSignCount();
        String format = data.getAttestationObject().getFormat();
        String transportsCsv =
                body.transports() == null ? null : String.join(",", body.transports());

        Authenticator saved =
                authenticatorRepository.save(
                        Authenticator.builder()
                                .userId(userId)
                                .credentialId(credentialId)
                                .publicKeyCose(publicKeyCose)
                                .signCount(signCount)
                                .attestationFmt(format)
                                .aaguid(aaguidValue)
                                .transports(transportsCsv)
                                .name(body.name())
                                .build());

        auditService.success(
                AuditAction.WEBAUTHN_REGISTER_COMPLETED, userId, user.getUsername(), body.name());

        return new RegistrationFinishResponse(saved.getId(), saved.getName());
    }

    private User loadUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static String challengeKey(UUID userId) {
        return CHALLENGE_KEY_PREFIX + userId;
    }

    private static String encodeUserHandle(UUID userId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(userId.getMostSignificantBits());
        buffer.putLong(userId.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private static UUID extractAaguid(AAGUID aaguid) {
        if (aaguid == null) {
            return null;
        }
        UUID value = aaguid.getValue();
        if (value == null
                || value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L) {
            return null;
        }
        return value;
    }
}
