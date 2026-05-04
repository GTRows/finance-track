package com.fintrack.auth.webauthn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.auth.UserRepository;
import com.fintrack.auth.webauthn.dto.RegistrationFinishRequest;
import com.fintrack.auth.webauthn.dto.RegistrationFinishResponse;
import com.fintrack.auth.webauthn.dto.RegistrationStartResponse;
import com.fintrack.common.entity.Authenticator;
import com.fintrack.common.entity.User;
import com.fintrack.common.exception.BusinessRuleException;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.CborConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.verifier.exception.BadAttestationStatementException;
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
class WebAuthnRegistrationServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String USERNAME = "ali";

    @Mock UserRepository userRepository;
    @Mock AuthenticatorRepository authenticatorRepository;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock WebAuthnManager webAuthnManager;
    @Mock ObjectConverter objectConverter;
    @Mock CborConverter cborConverter;
    @Mock AuditService auditService;

    private WebAuthnProperties properties;
    private WebAuthnRegistrationService service;

    @BeforeEach
    void setUp() {
        properties = new WebAuthnProperties("localhost", "FinTrack Pro", "http://localhost:5173");
        service =
                new WebAuthnRegistrationService(
                        userRepository,
                        authenticatorRepository,
                        redis,
                        webAuthnManager,
                        properties,
                        objectConverter,
                        auditService);
    }

    private User user() {
        return User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email(USERNAME + "@example.com")
                .password("hash")
                .role(User.Role.USER)
                .build();
    }

    @Test
    void startWritesBase64UrlChallengeToRedisWithFiveMinuteTtl() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(redis.opsForValue()).thenReturn(valueOps);

        RegistrationStartResponse response = service.start(USER_ID);

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCap = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCap.capture(), valueCap.capture(), ttlCap.capture());

        assertThat(keyCap.getValue()).isEqualTo("webauthn:reg:" + USER_ID);
        assertThat(ttlCap.getValue()).isEqualTo(Duration.ofMinutes(5));

        // 32 random bytes encoded base64url-without-padding => 43 characters.
        assertThat(valueCap.getValue()).hasSize(43);
        assertThat(Base64.getUrlDecoder().decode(valueCap.getValue())).hasSize(32);
        assertThat(response.challenge()).isEqualTo(valueCap.getValue());
        assertThat(response.rpId()).isEqualTo("localhost");
        assertThat(response.rpName()).isEqualTo("FinTrack Pro");
        assertThat(response.userName()).isEqualTo(USERNAME);
        assertThat(response.timeout()).isEqualTo(60_000L);
        assertThat(response.attestation()).isEqualTo("none");
        assertThat(response.userVerification()).isEqualTo("preferred");
        assertThat(response.residentKey()).isEqualTo("preferred");
        assertThat(response.pubKeyCredParams()).extracting("alg").containsExactly(-7L, -257L);

        verify(auditService).success(AuditAction.WEBAUTHN_REGISTER_STARTED, USER_ID, USERNAME);
    }

    @Test
    void finishHappyPathPersistsAuthenticatorAndAuditsCompletion() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(redis.opsForValue()).thenReturn(valueOps);
        // 32-byte challenge so DefaultChallenge can decode it.
        byte[] challengeBytes = new byte[32];
        for (int i = 0; i < challengeBytes.length; i++) {
            challengeBytes[i] = (byte) i;
        }
        String storedChallenge =
                Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        when(valueOps.getAndDelete("webauthn:reg:" + USER_ID)).thenReturn(storedChallenge);

        UUID aaguidUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        byte[] credentialIdBytes = new byte[] {1, 2, 3, 4, 5};
        COSEKey coseKey = Mockito.mock(COSEKey.class);
        AttestedCredentialData attested =
                new AttestedCredentialData(new AAGUID(aaguidUuid), credentialIdBytes, coseKey);

        AuthenticatorData<
                        com.webauthn4j.data.extension.authenticator
                                .RegistrationExtensionAuthenticatorOutput>
                authData = new AuthenticatorData<>(new byte[32], (byte) 0x41, 7L, attested);
        AttestationObject attestationObject =
                new AttestationObject(authData, new NoneAttestationStatement());
        RegistrationData data =
                new RegistrationData(attestationObject, new byte[0], null, new byte[0], null, null);

        when(webAuthnManager.parse(any(RegistrationRequest.class))).thenReturn(data);
        when(webAuthnManager.verify(any(RegistrationData.class), any(RegistrationParameters.class)))
                .thenReturn(data);

        when(objectConverter.getCborConverter()).thenReturn(cborConverter);
        byte[] cosePublicKey = new byte[] {9, 9, 9};
        when(cborConverter.writeValueAsBytes(any())).thenReturn(cosePublicKey);

        when(authenticatorRepository.save(any(Authenticator.class)))
                .thenAnswer(
                        inv -> {
                            Authenticator a = inv.getArgument(0);
                            a.setId(UUID.randomUUID());
                            return a;
                        });

        RegistrationFinishRequest body =
                new RegistrationFinishRequest(
                        "Yubikey 5",
                        "ignored-credential-id",
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {0}),
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1}),
                        List.of("usb", "nfc"));

        RegistrationFinishResponse response = service.finish(USER_ID, body);

        ArgumentCaptor<Authenticator> savedCap = ArgumentCaptor.forClass(Authenticator.class);
        verify(authenticatorRepository).save(savedCap.capture());
        Authenticator saved = savedCap.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getCredentialId()).isEqualTo(credentialIdBytes);
        assertThat(saved.getPublicKeyCose()).isEqualTo(cosePublicKey);
        assertThat(saved.getSignCount()).isEqualTo(7L);
        assertThat(saved.getAaguid()).isEqualTo(aaguidUuid);
        assertThat(saved.getTransports()).isEqualTo("usb,nfc");
        assertThat(saved.getName()).isEqualTo("Yubikey 5");

        assertThat(response.name()).isEqualTo("Yubikey 5");
        verify(auditService)
                .success(AuditAction.WEBAUTHN_REGISTER_COMPLETED, USER_ID, USERNAME, "Yubikey 5");
    }

    @Test
    void finishRejectsMissingChallengeAndAuditsFailure() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete(anyString())).thenReturn(null);

        RegistrationFinishRequest body =
                new RegistrationFinishRequest(
                        "Yubikey",
                        "cred",
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {0}),
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1}),
                        List.of());

        assertThatThrownBy(() -> service.finish(USER_ID, body))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("WEBAUTHN_CHALLENGE_INVALID");

        verify(auditService)
                .failure(
                        eq(AuditAction.WEBAUTHN_REGISTER_FAILED),
                        eq(USER_ID),
                        eq(USERNAME),
                        anyString());
        verify(authenticatorRepository, never()).save(any());
    }

    @Test
    void finishMapsLibraryFailureToWebAuthnRegistrationInvalid() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(redis.opsForValue()).thenReturn(valueOps);
        byte[] challengeBytes = new byte[32];
        when(valueOps.getAndDelete(anyString()))
                .thenReturn(Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes));
        when(webAuthnManager.parse(any(RegistrationRequest.class)))
                .thenThrow(new BadAttestationStatementException("attestation invalid"));

        RegistrationFinishRequest body =
                new RegistrationFinishRequest(
                        "Yubikey",
                        "cred",
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {0}),
                        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1}),
                        List.of());

        assertThatThrownBy(() -> service.finish(USER_ID, body))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getCode())
                .isEqualTo("WEBAUTHN_REGISTRATION_INVALID");

        verify(auditService, times(1))
                .failure(
                        eq(AuditAction.WEBAUTHN_REGISTER_FAILED),
                        eq(USER_ID),
                        eq(USERNAME),
                        anyString());
        verify(authenticatorRepository, never()).save(any());
    }
}
