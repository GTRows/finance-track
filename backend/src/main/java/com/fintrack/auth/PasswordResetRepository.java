package com.fintrack.auth;

import com.fintrack.common.entity.PasswordReset;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {

    Optional<PasswordReset> findByToken(String token);

    @Modifying
    @Query(
            "UPDATE PasswordReset p SET p.consumedAt = :now WHERE p.userId = :userId AND"
                    + " p.consumedAt IS NULL")
    int consumeOutstandingForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM PasswordReset p WHERE p.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
