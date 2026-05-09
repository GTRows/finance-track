package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.BudgetRule;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class BudgetRuleRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired BudgetRuleRepository repo;
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

    private BudgetRule rule(UUID userId, UUID categoryId, String limit) {
        return BudgetRule.builder()
                .userId(userId)
                .categoryId(categoryId)
                .monthlyLimitTry(new BigDecimal(limit))
                .build();
    }

    @Test
    void findByUserIdReturnsAll() {
        UUID userId = seedUser("ali");
        repo.save(rule(userId, UUID.randomUUID(), "1000"));
        repo.save(rule(userId, UUID.randomUUID(), "2000"));

        assertThat(repo.findByUserIdOrderByCreatedAtDesc(userId)).hasSize(2);
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ada");
        UUID b = seedUser("bob");
        BudgetRule own = repo.save(rule(a, UUID.randomUUID(), "500"));

        assertThat(repo.findByIdAndUserId(own.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(own.getId(), b)).isEmpty();
    }

    @Test
    void findByUserIdAndCategoryIdReturnsUniquePair() {
        UUID userId = seedUser("kemal");
        UUID category = UUID.randomUUID();
        repo.save(rule(userId, category, "800"));

        assertThat(repo.findByUserIdAndCategoryId(userId, category)).isPresent();
        assertThat(repo.findByUserIdAndCategoryId(userId, UUID.randomUUID())).isEmpty();
    }
}
