package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/** WebAuthn / passkey credential bound to a user. */
@Entity
@Table(name = "authenticators")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Authenticator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_id", nullable = false, unique = true)
    private byte[] credentialId;

    @Column(name = "public_key_cose", nullable = false)
    private byte[] publicKeyCose;

    @Column(name = "sign_count", nullable = false)
    @Builder.Default
    private long signCount = 0L;

    @Column(name = "attestation_fmt", length = 32)
    private String attestationFmt;

    @Column(name = "aaguid")
    private UUID aaguid;

    @Column(name = "transports", length = 64)
    private String transports;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
