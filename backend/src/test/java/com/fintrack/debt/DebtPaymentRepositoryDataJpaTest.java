package com.fintrack.debt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.PostgresDataJpaTest;
import com.fintrack.common.entity.Debt;
import com.fintrack.common.entity.DebtPayment;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresDataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class DebtPaymentRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired DebtPaymentRepository repo;
    @Autowired DebtRepository debtRepo;
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

    private Debt seedDebt(UUID userId) {
        return debtRepo.save(
                Debt.builder()
                        .userId(userId)
                        .name("Loan")
                        .debtType("PERSONAL")
                        .principal(new BigDecimal("100000"))
                        .annualRate(new BigDecimal("0.1500"))
                        .termMonths(60)
                        .startDate(LocalDate.of(2026, 1, 1))
                        .build());
    }

    private DebtPayment payment(UUID debtId, LocalDate date, String amount) {
        return DebtPayment.builder()
                .debtId(debtId)
                .paymentDate(date)
                .amount(new BigDecimal(amount))
                .build();
    }

    @Test
    void findByDebtIdOrdersByPaymentDateAsc() {
        UUID userId = seedUser("ali");
        Debt debt = seedDebt(userId);
        repo.save(payment(debt.getId(), LocalDate.of(2026, 3, 1), "100"));
        repo.save(payment(debt.getId(), LocalDate.of(2026, 1, 1), "100"));
        repo.save(payment(debt.getId(), LocalDate.of(2026, 2, 1), "100"));

        assertThat(repo.findByDebtIdOrderByPaymentDateAsc(debt.getId()))
                .extracting(DebtPayment::getPaymentDate)
                .containsExactly(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 3, 1));
    }

    @Test
    void sumByDebtIdAggregates() {
        UUID userId = seedUser("ada");
        Debt debt = seedDebt(userId);
        repo.save(payment(debt.getId(), LocalDate.of(2026, 1, 1), "100"));
        repo.save(payment(debt.getId(), LocalDate.of(2026, 2, 1), "250"));

        assertThat(repo.sumByDebtId(debt.getId())).isEqualByComparingTo("350");
    }

    @Test
    void sumByDebtIdReturnsZeroWhenEmpty() {
        assertThat(repo.sumByDebtId(UUID.randomUUID())).isEqualByComparingTo("0");
    }
}
