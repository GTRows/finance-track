package com.fintrack.audit;

import com.fintrack.common.entity.AuditLog;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    int deleteByCreatedAtBefore(Instant cutoff);

    @Modifying
    @Query(
            value =
                    "DELETE FROM audit_log WHERE id IN ("
                            + "SELECT id FROM audit_log WHERE created_at < :cutoff "
                            + "ORDER BY id LIMIT :limit)",
            nativeQuery = true)
    int deleteOldestBatch(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Query("SELECT MIN(a.createdAt) FROM AuditLog a")
    Optional<Instant> findOldestCreatedAt();
}
