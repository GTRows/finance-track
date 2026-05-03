package com.fintrack.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.AuditLog;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class AuditLogRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired AuditLogRepository repo;

    private AuditLog log(UUID userId, String action, AuditLog.Status status) {
        return AuditLog.builder()
                .userId(userId)
                .username("u-" + (userId == null ? "anon" : userId))
                .action(action)
                .status(status)
                .build();
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
}
