package com.fintrack.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Single-use recovery code for 2FA bypass. Stored as a BCrypt hash; the plaintext is only ever
 * shown once at generation time.
 */
@Entity
@Table(name = "totp_recovery_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
