package com.fintrack.auth.webauthn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.JwtUtil;
import com.fintrack.auth.RefreshTokenService;
import com.fintrack.auth.UserRepository;
import com.fintrack.auth.dto.AuthResponse;
import com.fintrack.auth.webauthn.dto.AssertionFinishRequest;
import com.fintrack.auth.webauthn.dto.AssertionStartResponse;
import com.fintrack.common.entity.Authenticator;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.CborConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class WebAuthnAssertionServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String USERNAME = "ali";

    @Mock UserRepository userRepository;
    @Mock AuthenticatorRepository authenticatorRepository;
    @Mock WebAuthnManager webAuthnManager;
    @Mock ObjectConverter objectConverter;
    @Mock CborConverter cborConverter;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtUtil jwtUtil;
    @Mock AuditService auditService;

    private WebAuthnProperties properties;
    private WebAuthnAssertionService service;

    @BeforeEach
    void setUp() {
        properties = new WebAuthnProperties("localhost", "FinTrack Pro", "http://localhost:5173");
        service =
                new WebAuthnAssertionService(
                        userRepository,
                        authenticatorRepository,
                        properties,
                        webAuthnManager,
                        objectConverter,
                        redis,
                        refreshTokenService,
                        jwtUtil,
                        auditService);
    }

    private User user() {
        return User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email(USERNAME + "@example.com")
                .password("hash")
                .role(User.Role.USER)
                .totpEnabled(false)
                .build();
    }

    private Authenticator authenticator() {
        return Authenticator.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .credentialId(new byte[] {1, 2, 3, 4})
                .publicKeyCose(new byte[] {9, 9, 9})
                .signCount(5L)
                .transports("usb,nfc")
                .name("Test Key")
                .build();
    }

    // (a) start with known username writes Redis and returns allowCredentials
    @Test
    void startKnownUserWritesRedisAndReturnsAllowCredentials() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user()));
        when(authenticatorRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(authenticator()));
        when(redis.opsForValue()).thenReturn(valueOps);

        AssertionStartResponse response = service.start(USERNAME);

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCap = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCap.capture(), valueCap.capture(), ttlCap.capture());

        assertThat(keyCap.getValue()).startsWith("webauthn:assert:");
        assertThat(valueCap.getValue()).isEqualTo(USER_ID.toString());
        assertThat(ttlCap.getValue()).isEqualTo(Duration.ofMinutes(5));

        assertThat(response.challenge()).hasSize(43); // 32 bytes -> 43 base64url chars
        assertThat(response.rpId()).isEqualTo("localhost");
        assertThat(response.allowCredentials()).hasSize(1);
        assertThat(response.allowCredentials().get(0).type()).isEqualTo("public-key");
        assertThat(response.allowCredentials().get(0).transports()).contains("usb", "nfc");
    }

    // (b) start with unknown username writes decoy Redis entry, not a real userId
    @Test
    void startUnknownUserWritesDecoyRedisEntryAndReturnsEmptyCredentials() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(redis.opsForValue()).thenReturn(valueOps);

        AssertionStartResponse response = service.start("ghost");

        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), valueCap.capture(), any(Duration.class));

        // The Redis value must NOT be parseable as a UUID (it is the decoy sentinel).
        assertThat(valueCap.getValue()).isEqualTo(WebAuthnAssertionService.DECOY_USER_ID);
        assertThat(response.allowCredentials()).isEmpty();
    }

    // (c) finish happy path: verifies signature, advances sign_count, returns accessToken
    @Test
    void finishHappyPathReturnsAccessToken() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);

        byte[] challenge = new byte[32];
        String encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);

        String clientDataJson =
                "{\"type\":\"webauthn.get\",\"challenge\":\""
                        + encodedChallenge
                        + "\",\"origin\":\"http://localhost:5173\"}";
        byte[] clientDataJsonBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);
        String encodedClientDataJson =
                Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataJsonBytes);

        when(valueOps.getAndDelete("webauthn:assert:" + encodedChallenge))
                .thenReturn(USER_ID.toString());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        Authenticator auth = authenticator();
        when(authenticatorRepository.findByCredentialId(any(byte[].class)))
                .thenReturn(Optional.of(auth));

        COSEKey mockCoseKey = Mockito.mock(COSEKey.class);
        when(objectConverter.getCborConverter()).thenReturn(cborConverter);
        when(cborConverter.readValue(any(byte[].class), eq(COSEKey.class))).thenReturn(mockCoseKey);

        com.webauthn4j.data.attestation.authenticator.AuthenticatorData<
                        com.webauthn4j.data.extension.authenticator
                                .AuthenticationExtensionAuthenticatorOutput>
                authData =
                        new com.webauthn4j.data.attestation.authenticator.AuthenticatorData<>(
                                new byte[32], (byte) 0x01, 10L);

        AuthenticationData mockAuthData = Mockito.mock(AuthenticationData.class);
        when(mockAuthData.getAuthenticatorData()).thenReturn(authData);
        when(webAuthnManager.parse(any(AuthenticationRequest.class))).thenReturn(mockAuthData);
        when(webAuthnManager.verify(
                        any(AuthenticationData.class), any(AuthenticationParameters.class)))
                .thenReturn(mockAuthData);

        when(authenticatorRepository.save(any(Authenticator.class))).thenReturn(auth);
        when(jwtUtil.generateAccessToken(anyString(), anyString(), anyString()))
                .thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(UUID.class), any(), any()))
                .thenReturn("refresh-token");
        when(jwtUtil.getAccessExpirySeconds()).thenReturn(900L);

        String encodedCredId =
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1, 2, 3, 4});
        AssertionFinishRequest body =
                new AssertionFinishRequest(
                        encodedCredId,
                        encodedClientDataJson,
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]),
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {5, 6}),
                        null);

        AuthResponse response = service.finish(body);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.requiresTotp()).isFalse();

        ArgumentCaptor<Authenticator> savedCap = ArgumentCaptor.forClass(Authenticator.class);
        verify(authenticatorRepository).save(savedCap.capture());
        assertThat(savedCap.getValue().getSignCount()).isEqualTo(10L);

        verify(auditService).success(eq(AuditAction.WEBAUTHN_LOGIN), eq(USER_ID), eq(USERNAME));
    }

    // (d) finish with non-increasing sign_count triggers clone detection
    @Test
    void finishCloneDetectionDeletesAuthenticatorAndThrows() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);

        byte[] challenge = new byte[32];
        String encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);

        String clientDataJson =
                "{\"type\":\"webauthn.get\",\"challenge\":\""
                        + encodedChallenge
                        + "\",\"origin\":\"http://localhost:5173\"}";
        byte[] clientDataJsonBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);
        String encodedClientDataJson =
                Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataJsonBytes);

        when(valueOps.getAndDelete("webauthn:assert:" + encodedChallenge))
                .thenReturn(USER_ID.toString());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        Authenticator auth = authenticator(); // signCount = 5
        when(authenticatorRepository.findByCredentialId(any(byte[].class)))
                .thenReturn(Optional.of(auth));

        COSEKey mockCoseKey = Mockito.mock(COSEKey.class);
        when(objectConverter.getCborConverter()).thenReturn(cborConverter);
        when(cborConverter.readValue(any(byte[].class), eq(COSEKey.class))).thenReturn(mockCoseKey);

        // Return a sign count of 3 — less than stored 5 — triggers clone detection.
        com.webauthn4j.data.attestation.authenticator.AuthenticatorData<
                        com.webauthn4j.data.extension.authenticator
                                .AuthenticationExtensionAuthenticatorOutput>
                authData =
                        new com.webauthn4j.data.attestation.authenticator.AuthenticatorData<>(
                                new byte[32], (byte) 0x01, 3L);

        AuthenticationData mockAuthData = Mockito.mock(AuthenticationData.class);
        when(mockAuthData.getAuthenticatorData()).thenReturn(authData);
        when(webAuthnManager.parse(any(AuthenticationRequest.class))).thenReturn(mockAuthData);
        when(webAuthnManager.verify(
                        any(AuthenticationData.class), any(AuthenticationParameters.class)))
                .thenReturn(mockAuthData);

        String encodedCredId =
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1, 2, 3, 4});
        AssertionFinishRequest body =
                new AssertionFinishRequest(
                        encodedCredId,
                        encodedClientDataJson,
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]),
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {5, 6}),
                        null);

        assertThatThrownBy(() -> service.finish(body))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("WEBAUTHN_CLONE_DETECTED");

        verify(authenticatorRepository).delete(auth);
        verify(auditService)
                .success(
                        eq(AuditAction.WEBAUTHN_CLONE_DETECTED),
                        eq(USER_ID),
                        eq(USERNAME),
                        anyString());
        verify(authenticatorRepository, never()).save(any());
    }

    // (e) finish with TOTP-enabled user returns challenge response, no refresh token
    @Test
    void finishTotpEnabledUserReturnsChallengeToken() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);

        byte[] challenge = new byte[32];
        String encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge);

        String clientDataJson =
                "{\"type\":\"webauthn.get\",\"challenge\":\""
                        + encodedChallenge
                        + "\",\"origin\":\"http://localhost:5173\"}";
        byte[] clientDataJsonBytes = clientDataJson.getBytes(StandardCharsets.UTF_8);
        String encodedClientDataJson =
                Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataJsonBytes);

        when(valueOps.getAndDelete("webauthn:assert:" + encodedChallenge))
                .thenReturn(USER_ID.toString());

        User totpUser =
                User.builder()
                        .id(USER_ID)
                        .username(USERNAME)
                        .email(USERNAME + "@example.com")
                        .password("hash")
                        .role(User.Role.USER)
                        .totpEnabled(true)
                        .totpSecret("TOTP_SECRET")
                        .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(totpUser));

        Authenticator auth = authenticator();
        when(authenticatorRepository.findByCredentialId(any(byte[].class)))
                .thenReturn(Optional.of(auth));

        COSEKey mockCoseKey = Mockito.mock(COSEKey.class);
        when(objectConverter.getCborConverter()).thenReturn(cborConverter);
        when(cborConverter.readValue(any(byte[].class), eq(COSEKey.class))).thenReturn(mockCoseKey);

        com.webauthn4j.data.attestation.authenticator.AuthenticatorData<
                        com.webauthn4j.data.extension.authenticator
                                .AuthenticationExtensionAuthenticatorOutput>
                authData =
                        new com.webauthn4j.data.attestation.authenticator.AuthenticatorData<>(
                                new byte[32], (byte) 0x01, 10L);

        AuthenticationData mockAuthData = Mockito.mock(AuthenticationData.class);
        when(mockAuthData.getAuthenticatorData()).thenReturn(authData);
        when(webAuthnManager.parse(any(AuthenticationRequest.class))).thenReturn(mockAuthData);
        when(webAuthnManager.verify(
                        any(AuthenticationData.class), any(AuthenticationParameters.class)))
                .thenReturn(mockAuthData);

        when(authenticatorRepository.save(any(Authenticator.class))).thenReturn(auth);
        when(jwtUtil.generateTotpChallengeToken(USER_ID.toString())).thenReturn("totp-challenge");

        String encodedCredId =
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1, 2, 3, 4});
        AssertionFinishRequest body =
                new AssertionFinishRequest(
                        encodedCredId,
                        encodedClientDataJson,
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]),
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {5, 6}),
                        null);

        AuthResponse response = service.finish(body);

        assertThat(response.requiresTotp()).isTrue();
        assertThat(response.totpChallengeToken()).isEqualTo("totp-challenge");
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();

        verify(auditService)
                .success(eq(AuditAction.TOTP_CHALLENGE_ISSUED), eq(USER_ID), eq(USERNAME));
    }
}
