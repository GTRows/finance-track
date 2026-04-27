package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class TransactionRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired TransactionRepository repo;
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

    private BudgetTransaction txn(UUID userId, TxnType type, String amount, LocalDate date) {
        return BudgetTransaction.builder()
                .userId(userId)
                .txnType(type)
                .amount(new BigDecimal(amount))
                .txnDate(date)
                .build();
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ali");
        UUID b = seedUser("baris");
        BudgetTransaction t = repo.save(txn(a, TxnType.EXPENSE, "100", LocalDate.of(2026, 4, 1)));

        assertThat(repo.findByIdAndUserId(t.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(t.getId(), b)).isEmpty();
    }

    @Test
    void pageByDateRangeOrdersDescending() {
        UUID userId = seedUser("ada");
        repo.save(txn(userId, TxnType.EXPENSE, "1", LocalDate.of(2026, 4, 1)));
        repo.save(txn(userId, TxnType.EXPENSE, "2", LocalDate.of(2026, 4, 15)));
        repo.save(txn(userId, TxnType.EXPENSE, "3", LocalDate.of(2026, 4, 30)));

        var page =
                repo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                        userId,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 30),
                        PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(BudgetTransaction::getAmount)
                .extracting(BigDecimal::intValue)
                .containsExactly(3, 2, 1);
    }

    @Test
    void filterByTypeOnlyReturnsMatchingType() {
        UUID userId = seedUser("kemal");
        repo.save(txn(userId, TxnType.INCOME, "1000", LocalDate.of(2026, 4, 5)));
        repo.save(txn(userId, TxnType.EXPENSE, "200", LocalDate.of(2026, 4, 5)));

        var page =
                repo.findByUserIdAndTxnTypeAndTxnDateBetweenOrderByTxnDateDesc(
                        userId,
                        TxnType.INCOME,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 30),
                        PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(BudgetTransaction::getTxnType)
                .containsOnly(TxnType.INCOME);
    }

    @Test
    void sumByUserAndTypeAggregatesAmount() {
        UUID userId = seedUser("ozge");
        repo.save(txn(userId, TxnType.EXPENSE, "100", LocalDate.of(2026, 4, 1)));
        repo.save(txn(userId, TxnType.EXPENSE, "250", LocalDate.of(2026, 4, 10)));
        repo.save(txn(userId, TxnType.INCOME, "5000", LocalDate.of(2026, 4, 15)));

        BigDecimal expense =
                repo.sumByUserIdAndTypeAndDateRange(
                        userId,
                        TxnType.EXPENSE,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 30));

        assertThat(expense).isEqualByComparingTo("350");
    }

    @Test
    void sumOutsideRangeReturnsZero() {
        UUID userId = seedUser("yusuf");
        repo.save(txn(userId, TxnType.EXPENSE, "100", LocalDate.of(2026, 4, 1)));

        BigDecimal sum =
                repo.sumByUserIdAndTypeAndDateRange(
                        userId,
                        TxnType.EXPENSE,
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31));

        assertThat(sum).isEqualByComparingTo("0");
    }
}
