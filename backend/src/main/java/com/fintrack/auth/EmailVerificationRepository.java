package com.fintrack.auth;

import com.fintrack.common.entity.EmailVerification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findByToken(String token);

    /** Invalidate any outstanding tokens for a user. */
    @Modifying
    @Query(
            "UPDATE EmailVerification e SET e.consumedAt = :now WHERE e.userId = :userId AND"
                    + " e.consumedAt IS NULL")
    int consumeOutstandingForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM EmailVerification e WHERE e.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
