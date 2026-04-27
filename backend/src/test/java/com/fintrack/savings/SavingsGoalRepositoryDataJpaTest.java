package com.fintrack.savings;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.SavingsGoal;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class SavingsGoalRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired SavingsGoalRepository repo;
    @Autowired UserRepository userRepo;

    private UUID seedUser(String username) {
        return userRepo.save(
                        User.builder()
                                .username(username)
                                .email(username + "@example.com")
                                .password("bcrypt-hash")
                                .role(User.Role.USER)
                                .build())
                .getId();
    }

    private SavingsGoal goal(UUID userId, String name, Instant archivedAt) {
        return SavingsGoal.builder()
                .userId(userId)
                .name(name)
                .targetAmount(new BigDecimal("60000"))
                .archivedAt(archivedAt)
                .build();
    }

    @Test
    void findActiveExcludesArchived() {
        UUID userId = seedUser("ali");
        repo.save(goal(userId, "Live", null));
        repo.save(goal(userId, "Old", Instant.parse("2025-01-01T00:00:00Z")));

        assertThat(repo.findActive(userId))
                .extracting(SavingsGoal::getName)
                .containsExactly("Live");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ada");
        UUID b = seedUser("bob");
        SavingsGoal aGoal = repo.save(goal(a, "X", null));

        assertThat(repo.findByIdAndUserId(aGoal.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(aGoal.getId(), b)).isEmpty();
    }

    @Test
    void findByUserIdReturnsActiveAndArchived() {
        UUID userId = seedUser("kemal");
        repo.save(goal(userId, "Live", null));
        repo.save(goal(userId, "Old", Instant.parse("2025-01-01T00:00:00Z")));

        assertThat(repo.findByUserIdOrderByCreatedAtAsc(userId))
                .extracting(SavingsGoal::getName)
                .containsExactlyInAnyOrder("Live", "Old");
    }
}
