package com.fintrack.savings;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.SavingsGoal;
import com.fintrack.common.entity.SavingsGoalContribution;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class SavingsContributionRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired SavingsContributionRepository repo;
    @Autowired SavingsGoalRepository goalRepo;
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

    private UUID seedGoal(UUID userId) {
        return goalRepo.save(
                        SavingsGoal.builder()
                                .userId(userId)
                                .name("Vacation")
                                .targetAmount(new BigDecimal("60000"))
                                .build())
                .getId();
    }

    private SavingsGoalContribution contribution(UUID goalId, LocalDate date, String amount) {
        return SavingsGoalContribution.builder()
                .goalId(goalId)
                .contributionDate(date)
                .amount(new BigDecimal(amount))
                .build();
    }

    @Test
    void findByGoalIdOrdersByDateDesc() {
        UUID userId = seedUser("ali");
        UUID goal = seedGoal(userId);
        repo.save(contribution(goal, LocalDate.of(2026, 1, 1), "100"));
        repo.save(contribution(goal, LocalDate.of(2026, 4, 1), "300"));
        repo.save(contribution(goal, LocalDate.of(2026, 2, 1), "200"));

        assertThat(repo.findByGoalIdOrderByContributionDateDesc(goal))
                .extracting(SavingsGoalContribution::getContributionDate)
                .containsExactly(
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1));
    }

    @Test
    void sumByGoalIdAggregates() {
        UUID userId = seedUser("ada");
        UUID goal = seedGoal(userId);
        repo.save(contribution(goal, LocalDate.of(2026, 1, 1), "100"));
        repo.save(contribution(goal, LocalDate.of(2026, 2, 1), "250"));

        assertThat(repo.sumByGoalId(goal)).isEqualByComparingTo("350");
    }

    @Test
    void sumByGoalIdReturnsZeroWhenEmpty() {
        assertThat(repo.sumByGoalId(UUID.randomUUID())).isEqualByComparingTo("0");
    }

    @Test
    void deleteByGoalIdRemovesAll() {
        UUID userId = seedUser("kemal");
        UUID goal = seedGoal(userId);
        repo.save(contribution(goal, LocalDate.of(2026, 1, 1), "100"));

        repo.deleteByGoalId(goal);

        assertThat(repo.findByGoalIdOrderByContributionDateDesc(goal)).isEmpty();
    }
}
