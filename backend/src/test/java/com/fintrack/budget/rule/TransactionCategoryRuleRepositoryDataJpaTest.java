package com.fintrack.budget.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.TransactionCategoryRule;
import com.fintrack.common.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class TransactionCategoryRuleRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired TransactionCategoryRuleRepository repo;
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

    private TransactionCategoryRule rule(
            UUID userId, String pattern, BudgetTransaction.TxnType type, int priority) {
        return TransactionCategoryRule.builder()
                .userId(userId)
                .pattern(pattern)
                .categoryId(UUID.randomUUID())
                .txnType(type)
                .priority(priority)
                .build();
    }

    @Test
    void findByUserIdAndTxnTypeOrdersByPriorityAsc() {
        UUID userId = seedUser("ali");
        repo.save(rule(userId, "rent", BudgetTransaction.TxnType.EXPENSE, 50));
        repo.save(rule(userId, "salary", BudgetTransaction.TxnType.INCOME, 10));
        repo.save(rule(userId, "groceries", BudgetTransaction.TxnType.EXPENSE, 5));

        var expenseRules =
                repo.findByUserIdAndTxnTypeOrderByPriorityAscCreatedAtAsc(
                        userId, BudgetTransaction.TxnType.EXPENSE);

        assertThat(expenseRules)
                .extracting(TransactionCategoryRule::getPattern)
                .containsExactly("groceries", "rent");
    }

    @Test
    void findByUserIdReturnsAllOrdered() {
        UUID userId = seedUser("ada");
        repo.save(rule(userId, "a", BudgetTransaction.TxnType.EXPENSE, 100));
        repo.save(rule(userId, "b", BudgetTransaction.TxnType.INCOME, 1));

        var all = repo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId);

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getPattern()).isEqualTo("b");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("anna");
        UUID b = seedUser("bob");
        TransactionCategoryRule own =
                repo.save(rule(a, "x", BudgetTransaction.TxnType.EXPENSE, 100));

        assertThat(repo.findByIdAndUserId(own.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(own.getId(), b)).isEmpty();
    }
}
