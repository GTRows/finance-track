package com.fintrack.auth.webauthn;

import com.fintrack.common.entity.Authenticator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence operations for WebAuthn authenticator credentials. */
public interface AuthenticatorRepository extends JpaRepository<Authenticator, UUID> {

    /** Lists a user's authenticators, newest first. */
    List<Authenticator> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Looks up an authenticator by its raw credential id. */
    Optional<Authenticator> findByCredentialId(byte[] credentialId);

    /** Loads an authenticator scoped to its owner. */
    Optional<Authenticator> findByIdAndUserId(UUID id, UUID userId);

    /** Removes an authenticator scoped to its owner. */
    void deleteByIdAndUserId(UUID id, UUID userId);
}
