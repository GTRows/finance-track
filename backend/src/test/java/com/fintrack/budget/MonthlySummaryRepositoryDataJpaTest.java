package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.MonthlySummary;
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
class MonthlySummaryRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired MonthlySummaryRepository repo;
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

    private MonthlySummary summary(UUID userId, String period) {
        return MonthlySummary.builder()
                .userId(userId)
                .period(period)
                .totalIncome(new BigDecimal("5000"))
                .totalExpense(new BigDecimal("3000"))
                .build();
    }

    @Test
    void findByUserIdOrdersPeriodDesc() {
        UUID userId = seedUser("ali");
        repo.save(summary(userId, "2026-01"));
        repo.save(summary(userId, "2026-03"));
        repo.save(summary(userId, "2026-02"));

        assertThat(repo.findByUserIdOrderByPeriodDesc(userId))
                .extracting(MonthlySummary::getPeriod)
                .containsExactly("2026-03", "2026-02", "2026-01");
    }

    @Test
    void findByUserIdAndPeriodReturnsExactMatch() {
        UUID userId = seedUser("ada");
        repo.save(summary(userId, "2026-04"));

        assertThat(repo.findByUserIdAndPeriod(userId, "2026-04")).isPresent();
        assertThat(repo.findByUserIdAndPeriod(userId, "2026-99")).isEmpty();
    }
}
