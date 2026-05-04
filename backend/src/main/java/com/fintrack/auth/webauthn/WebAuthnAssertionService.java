package com.fintrack.auth.webauthn;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.JwtUtil;
import com.fintrack.auth.RefreshTokenService;
import com.fintrack.auth.UserRepository;
import com.fintrack.auth.dto.AuthResponse;
import com.fintrack.auth.dto.UserProfileResponse;
import com.fintrack.auth.webauthn.dto.AllowedCredential;
import com.fintrack.auth.webauthn.dto.AssertionFinishRequest;
import com.fintrack.auth.webauthn.dto.AssertionStartResponse;
import com.fintrack.common.entity.Authenticator;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.common.web.RequestContext;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.AuthenticatorTransport;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.Base64UrlUtil;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Drives the WebAuthn assertion (login) ceremony (start + finish). */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebAuthnAssertionService {

    static final String CHALLENGE_KEY_PREFIX = "webauthn:assert:";
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    static final long ASSERTION_TIMEOUT_MS = 60_000L;
    static final int CHALLENGE_BYTES = 32;

    /** Sentinel stored in Redis for unknown usernames to resist timing-based enumeration. */
    static final String DECOY_USER_ID = "decoy";

    private final UserRepository userRepository;
    private final AuthenticatorRepository authenticatorRepository;
    private final WebAuthnProperties properties;
    private final WebAuthnManager webAuthnManager;
    private final ObjectConverter objectConverter;
    private final StringRedisTemplate redis;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    /**
     * Issues a fresh challenge and returns the navigator.credentials.get() options. For unknown
     * usernames a generic challenge is still issued (with a decoy Redis entry) to avoid timing
     * leaks.
     */
    @Transactional(readOnly = true)
    public AssertionStartResponse start(String username) {
        byte[] challenge = new byte[CHALLENGE_BYTES];
        new SecureRandom().nextBytes(challenge);
        String encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);

        Optional<User> userOpt = userRepository.findByUsername(username);

        List<AllowedCredential> allowCredentials;
        String redisValue;

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            redisValue = user.getId().toString();
            List<Authenticator> authenticators =
                    authenticatorRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
            allowCredentials =
                    authenticators.stream()
                            .map(
                                    a -> {
                                        String credId =
                                                Base64.getUrlEncoder()
                                                        .withoutPadding()
                                                        .encodeToString(a.getCredentialId());
                                        List<String> transports =
                                                a.getTransports() != null
                                                        ? Arrays.asList(
                                                                a.getTransports().split(","))
                                                        : List.of();
                                        return new AllowedCredential(
                                                "public-key", credId, transports);
                                    })
                            .toList();
        } else {
            redisValue = DECOY_USER_ID;
            allowCredentials = List.of();
        }

        redis.opsForValue().set(challengeKey(encodedChallenge), redisValue, CHALLENGE_TTL);

        return new AssertionStartResponse(
                encodedChallenge,
                properties.rpId(),
                ASSERTION_TIMEOUT_MS,
                allowCredentials,
                "preferred");
    }

    /**
     * Validates the browser's assertion response, advances the sign count, and returns a full auth
     * response. Throws if the challenge is missing/expired, credential unknown, signature invalid,
     * or sign count indicates a cloned authenticator.
     */
    @Transactional
    public AuthResponse finish(AssertionFinishRequest body) {
        byte[] credentialIdBytes = Base64UrlUtil.decode(body.credentialId());

        // Locate the stored challenge by scanning the Redis key derived from the credential —
        // the client echoes back the same credentialId in its response, but the challenge lives
        // in a Redis key keyed by the base64url-encoded challenge bytes from the request.
        // The client must also supply authenticatorData which contains the RP-bound challenge
        // indirectly; we trust the Redis key we stored during start().
        //
        // We find the Authenticator first (by credentialId) to identify the user, then look up
        // the Redis entry we stored at start() keyed by the base64url challenge.
        // But we stored the challenge key as webauthn:assert:<encodedChallenge>, and the client
        // sends back clientDataJSON which (when decoded) contains the original challenge.
        // We decode clientDataJSON to extract the challenge and do the lookup atomically.

        byte[] clientDataJsonBytes = Base64UrlUtil.decode(body.clientDataJSON());
        byte[] authenticatorDataBytes = Base64UrlUtil.decode(body.authenticatorData());
        byte[] signatureBytes = Base64UrlUtil.decode(body.signature());

        // Parse clientDataJSON to extract the challenge field.
        String encodedChallenge = extractChallengeFromClientDataJson(clientDataJsonBytes);
        if (encodedChallenge == null) {
            auditService.failure(
                    AuditAction.WEBAUTHN_LOGIN_FAILED,
                    body.credentialId(),
                    "malformed clientDataJSON");
            throw new BusinessRuleException(
                    "Assertion failed: malformed clientDataJSON", "WEBAUTHN_ASSERTION_INVALID");
        }

        String redisValue = redis.opsForValue().getAndDelete(challengeKey(encodedChallenge));
        if (redisValue == null || DECOY_USER_ID.equals(redisValue)) {
            auditService.failure(
                    AuditAction.WEBAUTHN_LOGIN_FAILED,
                    (String) null,
                    "missing or expired challenge");
            throw new BusinessRuleException(
                    "Assertion challenge invalid or expired", "WEBAUTHN_CHALLENGE_INVALID");
        }

        UUID userId;
        try {
            userId = UUID.fromString(redisValue);
        } catch (IllegalArgumentException e) {
            auditService.failure(
                    AuditAction.WEBAUTHN_LOGIN_FAILED, (String) null, "corrupt Redis entry");
            throw new BusinessRuleException(
                    "Assertion challenge invalid or expired", "WEBAUTHN_CHALLENGE_INVALID");
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Authenticator authenticator =
                authenticatorRepository
                        .findByCredentialId(credentialIdBytes)
                        .orElseGet(
                                () -> {
                                    auditService.failure(
                                            AuditAction.WEBAUTHN_LOGIN_FAILED,
                                            userId,
                                            user.getUsername(),
                                            "credential not found");
                                    throw new BusinessRuleException(
                                            "Credential not found",
                                            "WEBAUTHN_CREDENTIAL_NOT_FOUND");
                                });

        // Deserialize the stored COSE public key back to a COSEKey object for verification.
        com.webauthn4j.data.attestation.authenticator.COSEKey coseKey;
        try {
            coseKey =
                    objectConverter
                            .getCborConverter()
                            .readValue(
                                    authenticator.getPublicKeyCose(),
                                    com.webauthn4j.data.attestation.authenticator.COSEKey.class);
        } catch (Exception ex) {
            log.warn("Failed to deserialize COSE key for credential {}", body.credentialId());
            auditService.failure(
                    AuditAction.WEBAUTHN_LOGIN_FAILED,
                    userId,
                    user.getUsername(),
                    "COSE key deserialization failed");
            throw new BusinessRuleException("Assertion failed", "WEBAUTHN_ASSERTION_INVALID");
        }

        byte[] challengeBytes = Base64UrlUtil.decode(encodedChallenge);

        Set<AuthenticatorTransport> transportSet = buildTransportSet(authenticator.getTransports());

        com.webauthn4j.authenticator.AuthenticatorImpl webAuthn4jAuthenticator =
                new com.webauthn4j.authenticator.AuthenticatorImpl(
                        new com.webauthn4j.data.attestation.authenticator.AttestedCredentialData(
                                com.webauthn4j.data.attestation.authenticator.AAGUID.ZERO,
                                credentialIdBytes,
                                coseKey),
                        null,
                        authenticator.getSignCount(),
                        transportSet != null && !transportSet.isEmpty() ? transportSet : null);

        ServerProperty serverProperty =
                new ServerProperty(
                        new Origin(properties.origin()),
                        properties.rpId(),
                        new DefaultChallenge(challengeBytes),
                        null);

        AuthenticationRequest authRequest =
                new AuthenticationRequest(
                        credentialIdBytes,
                        authenticatorDataBytes,
                        clientDataJsonBytes,
                        signatureBytes);

        AuthenticationParameters authParameters =
                new AuthenticationParameters(serverProperty, webAuthn4jAuthenticator, false, true);

        AuthenticationData authData;
        try {
            authData = webAuthnManager.parse(authRequest);
            webAuthnManager.verify(authData, authParameters);
        } catch (Exception ex) {
            log.warn("WebAuthn assertion verification failed: {}", ex.getMessage());
            auditService.failure(
                    AuditAction.WEBAUTHN_LOGIN_FAILED,
                    userId,
                    user.getUsername(),
                    "signature invalid");
            throw new BusinessRuleException("Assertion failed", "WEBAUTHN_ASSERTION_INVALID");
        }

        long newSignCount = authData.getAuthenticatorData().getSignCount();
        long storedSignCount = authenticator.getSignCount();

        // Clone detection: new sign count must be strictly greater than stored.
        if (newSignCount != 0 && newSignCount <= storedSignCount) {
            log.warn(
                    "Clone detected for credential {} (stored={}, received={})",
                    body.credentialId(),
                    storedSignCount,
                    newSignCount);
            authenticatorRepository.delete(authenticator);
            auditService.success(
                    AuditAction.WEBAUTHN_CLONE_DETECTED,
                    userId,
                    user.getUsername(),
                    "credentialId=" + body.credentialId());
            throw new BusinessRuleException(
                    "Authenticator integrity check failed", "WEBAUTHN_CLONE_DETECTED");
        }

        authenticator.setSignCount(newSignCount);
        authenticator.setLastUsedAt(Instant.now());
        authenticatorRepository.save(authenticator);

        if (user.isTotpEnabled()) {
            String challengeToken = jwtUtil.generateTotpChallengeToken(user.getId().toString());
            log.info(
                    "TOTP challenge issued after WebAuthn assertion for user: {}",
                    user.getUsername());
            auditService.success(
                    AuditAction.TOTP_CHALLENGE_ISSUED, user.getId(), user.getUsername());
            return AuthResponse.challenge(challengeToken);
        }

        log.info("WebAuthn login successful for user: {}", user.getUsername());
        auditService.success(AuditAction.WEBAUTHN_LOGIN, user.getId(), user.getUsername());
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken =
                jwtUtil.generateAccessToken(
                        user.getId().toString(), user.getUsername(), user.getRole().name());
        String refreshToken =
                refreshTokenService.createRefreshToken(
                        user.getId(), RequestContext.userAgent(), RequestContext.clientIp());
        return AuthResponse.of(
                accessToken, refreshToken, jwtUtil.getAccessExpirySeconds(), toProfile(user));
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
    }

    private static String challengeKey(String encodedChallenge) {
        return CHALLENGE_KEY_PREFIX + encodedChallenge;
    }

    /**
     * Extracts the {@code challenge} field from a base64url-encoded clientDataJSON byte array.
     * Returns null if parsing fails.
     */
    private static String extractChallengeFromClientDataJson(byte[] clientDataJsonBytes) {
        try {
            String json = new String(clientDataJsonBytes, java.nio.charset.StandardCharsets.UTF_8);
            // Simple JSON field extraction without a full parser to avoid an extra dependency.
            int idx = json.indexOf("\"challenge\"");
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            if (colon < 0) return null;
            int quote1 = json.indexOf('"', colon + 1);
            if (quote1 < 0) return null;
            int quote2 = json.indexOf('"', quote1 + 1);
            if (quote2 < 0) return null;
            return json.substring(quote1 + 1, quote2);
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<AuthenticatorTransport> buildTransportSet(String transportsCsv) {
        if (transportsCsv == null || transportsCsv.isBlank()) {
            return Set.of();
        }
        java.util.Set<AuthenticatorTransport> result = new java.util.HashSet<>();
        for (String t : transportsCsv.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                try {
                    result.add(AuthenticatorTransport.create(trimmed));
                } catch (Exception ignored) {
                    // Unknown transport string — skip rather than fail.
                }
            }
        }
        return result;
    }
}
