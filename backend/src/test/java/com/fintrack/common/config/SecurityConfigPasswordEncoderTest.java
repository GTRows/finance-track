package com.fintrack.common.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigPasswordEncoderTest {

    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2", encoders);
        delegating.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder(12));
        encoder = delegating;
    }

    @Test
    void encodeProducesArgon2idPrefix() {
        String hash = encoder.encode("pw");
        assertTrue(
                hash.startsWith("{argon2}$argon2id$"),
                "Expected {argon2}$argon2id$ prefix, got: " + hash);
    }

    @Test
    void matchesPrefixedBcryptHash() {
        String bcryptRaw = new BCryptPasswordEncoder(12).encode("pw");
        String prefixed = "{bcrypt}" + bcryptRaw;
        assertTrue(encoder.matches("pw", prefixed));
    }

    @Test
    void matchesLegacyUnprefixedBcryptHash() {
        String bcryptRaw = new BCryptPasswordEncoder(12).encode("pw");
        assertTrue(encoder.matches("pw", bcryptRaw));
    }

    @Test
    void upgradeEncodingTrueForBcryptHash() {
        String bcryptRaw = new BCryptPasswordEncoder(12).encode("pw");
        assertTrue(encoder.upgradeEncoding(bcryptRaw));
    }

    @Test
    void upgradeEncodingFalseForArgon2Hash() {
        String argonHash = encoder.encode("pw");
        assertFalse(encoder.upgradeEncoding(argonHash));
    }
}
