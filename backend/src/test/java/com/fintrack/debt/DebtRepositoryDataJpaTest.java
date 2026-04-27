package com.fintrack.debt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Debt;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class DebtRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired DebtRepository repo;
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

    private Debt debt(UUID userId, String name, LocalDate start, Instant archivedAt) {
        return Debt.builder()
                .userId(userId)
                .name(name)
                .debtType("MORTGAGE")
                .principal(new BigDecimal("100000"))
                .annualRate(new BigDecimal("0.1500"))
                .termMonths(120)
                .startDate(start)
                .archivedAt(archivedAt)
                .build();
    }

    @Test
    void findActiveExcludesArchived() {
        UUID userId = seedUser("ali");
        repo.save(debt(userId, "Live", LocalDate.of(2026, 1, 1), null));
        repo.save(
                debt(
                        userId,
                        "Old",
                        LocalDate.of(2024, 1, 1),
                        Instant.parse("2025-01-01T00:00:00Z")));

        assertThat(repo.findActive(userId)).extracting(Debt::getName).containsExactly("Live");
    }

    @Test
    void findActiveOrdersByStartThenCreated() {
        UUID userId = seedUser("ada");
        repo.save(debt(userId, "B", LocalDate.of(2026, 3, 1), null));
        repo.save(debt(userId, "A", LocalDate.of(2026, 1, 1), null));
        repo.save(debt(userId, "C", LocalDate.of(2026, 5, 1), null));

        assertThat(repo.findActive(userId))
                .extracting(Debt::getName)
                .containsExactly("A", "B", "C");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("anna");
        UUID b = seedUser("ben");
        Debt aDebt = repo.save(debt(a, "X", LocalDate.of(2026, 1, 1), null));

        assertThat(repo.findByIdAndUserId(aDebt.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(aDebt.getId(), b)).isEmpty();
    }
}
