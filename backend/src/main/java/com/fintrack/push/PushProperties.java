package com.fintrack.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAPID keys and contact subject for Web Push. If either key is blank on
 * startup, {@link VapidKeyManager} generates a new pair and logs it so the
 * operator can persist it in the environment.
 *
 * <p>Keys are URL-safe base64 (unpadded): the public key is the 65-byte
 * uncompressed EC point (0x04 || X || Y); the private key is the 32-byte
 * scalar.</p>
 */
@ConfigurationProperties(prefix = "fintrack.push")
public record PushProperties(
        String vapidPublicKey,
        String vapidPrivateKey,
        String subject
) {
}
