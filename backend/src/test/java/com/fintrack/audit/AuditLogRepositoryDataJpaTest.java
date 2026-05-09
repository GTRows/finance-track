package com.fintrack.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.AuditLog;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class AuditLogRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired AuditLogRepository repo;
    @Autowired EntityManager em;

    private AuditLog log(UUID userId, String action, AuditLog.Status status) {
        return AuditLog.builder()
                .userId(userId)
                .username("u-" + (userId == null ? "anon" : userId))
                .action(action)
                .status(status)
                .build();
    }

    private Long persistWithCreatedAt(Instant createdAt) {
        AuditLog entry = repo.save(log(UUID.randomUUID(), "ACT", AuditLog.Status.SUCCESS));
        em.flush();
        em.createNativeQuery("UPDATE audit_log SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, entry.getId())
                .executeUpdate();
        em.clear();
        return entry.getId();
    }

    @Test
    void findAllByOrderByCreatedAtDescPaginates() {
        repo.save(log(UUID.randomUUID(), "LOGIN", AuditLog.Status.SUCCESS));
        repo.save(log(UUID.randomUUID(), "LOGIN", AuditLog.Status.FAILURE));

        var page = repo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findByUserIdOrderByCreatedAtDescScopesToUser() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        repo.save(log(alice, "LOGIN", AuditLog.Status.SUCCESS));
        repo.save(log(bob, "LOGIN", AuditLog.Status.SUCCESS));

        var page = repo.findByUserIdOrderByCreatedAtDesc(alice, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AuditLog::getUserId).containsOnly(alice);
    }

    @Test
    void findByActionOrderByCreatedAtDescFiltersByAction() {
        UUID userId = UUID.randomUUID();
        repo.save(log(userId, "LOGIN", AuditLog.Status.SUCCESS));
        repo.save(log(userId, "LOGOUT", AuditLog.Status.SUCCESS));

        var page = repo.findByActionOrderByCreatedAtDesc("LOGIN", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AuditLog::getAction).containsOnly("LOGIN");
    }

    @Test
    void findByActionReturnsEmptyForUnknownAction() {
        var page = repo.findByActionOrderByCreatedAtDesc("MISSING", PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void deleteOldestBatchKeepsRowsWithinRetention() {
        Instant now = Instant.now();
        persistWithCreatedAt(now.minus(200, ChronoUnit.DAYS));
        persistWithCreatedAt(now.minus(180, ChronoUnit.DAYS));
        persistWithCreatedAt(now.minus(100, ChronoUnit.DAYS));
        persistWithCreatedAt(now.minus(30, ChronoUnit.DAYS));
        persistWithCreatedAt(now.minus(5, ChronoUnit.DAYS));

        Instant cutoff = now.minus(90, ChronoUnit.DAYS);
        int deleted = repo.deleteOldestBatch(cutoff, 100);

        assertThat(deleted).isEqualTo(3);
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    void deleteOldestBatchRespectsLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            persistWithCreatedAt(now.minus(120 + i, ChronoUnit.DAYS));
        }

        Instant cutoff = now.minus(90, ChronoUnit.DAYS);
        int first = repo.deleteOldestBatch(cutoff, 2);
        int second = repo.deleteOldestBatch(cutoff, 2);
        int third = repo.deleteOldestBatch(cutoff, 2);
        int fourth = repo.deleteOldestBatch(cutoff, 2);

        assertThat(first).isEqualTo(2);
        assertThat(second).isEqualTo(2);
        assertThat(third).isEqualTo(1);
        assertThat(fourth).isEqualTo(0);
        assertThat(repo.count()).isZero();
    }
}
