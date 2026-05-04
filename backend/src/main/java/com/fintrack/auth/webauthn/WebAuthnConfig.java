package com.fintrack.auth.webauthn;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.ObjectConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the webauthn4j primary entry points as Spring beans. */
@Configuration
@EnableConfigurationProperties(WebAuthnProperties.class)
public class WebAuthnConfig {

    /** Shared converter used for both ceremony parsing and COSE-key serialization. */
    @Bean
    public ObjectConverter webAuthnObjectConverter() {
        return new ObjectConverter();
    }

    /** Non-strict manager: skips full attestation cert-chain validation in dev. */
    @Bean
    public WebAuthnManager webAuthnManager(ObjectConverter objectConverter) {
        return WebAuthnManager.createNonStrictWebAuthnManager(objectConverter);
    }
}
