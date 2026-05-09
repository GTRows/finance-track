package com.fintrack.budget.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.PostgresDataJpaTest;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.RecurringTemplate;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresDataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class RecurringTemplateRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired RecurringTemplateRepository repo;
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

    private RecurringTemplate template(UUID userId, String description, boolean active) {
        return RecurringTemplate.builder()
                .userId(userId)
                .txnType(BudgetTransaction.TxnType.EXPENSE)
                .amount(new BigDecimal("100"))
                .description(description)
                .dayOfMonth(1)
                .active(active)
                .build();
    }

    @Test
    void findByUserIdReturnsAllOrdered() {
        UUID userId = seedUser("ali");
        repo.save(template(userId, "Rent", true));
        repo.save(template(userId, "Phone", false));

        assertThat(repo.findByUserIdOrderByCreatedAtAsc(userId)).hasSize(2);
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ada");
        UUID b = seedUser("bob");
        RecurringTemplate own = repo.save(template(a, "X", true));

        assertThat(repo.findByIdAndUserId(own.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(own.getId(), b)).isEmpty();
    }

    @Test
    void findByActiveTrueReturnsOnlyActive() {
        UUID userId = seedUser("kemal");
        repo.save(template(userId, "Live", true));
        repo.save(template(userId, "Off", false));

        assertThat(repo.findByActiveTrue())
                .extracting(RecurringTemplate::getDescription)
                .containsExactly("Live");
    }
}
