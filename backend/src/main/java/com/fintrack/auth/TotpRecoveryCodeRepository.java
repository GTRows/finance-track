package com.fintrack.auth;

import com.fintrack.common.entity.TotpRecoveryCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, UUID> {

    @Query("SELECT c FROM TotpRecoveryCode c WHERE c.userId = :userId AND c.consumedAt IS NULL")
    List<TotpRecoveryCode> findActiveByUserId(@Param("userId") UUID userId);

    long countByUserIdAndConsumedAtIsNull(UUID userId);

    @Modifying
    @Query("DELETE FROM TotpRecoveryCode c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
