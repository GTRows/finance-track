package com.fintrack.bills;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class BillRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired BillRepository repo;
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

    private Bill bill(UUID userId, String name, int dueDay, boolean active) {
        return Bill.builder()
                .userId(userId)
                .name(name)
                .amount(new BigDecimal("100"))
                .dueDay(dueDay)
                .active(active)
                .build();
    }

    @Test
    void findByUserIdReturnsAllOrderedByDueDay() {
        UUID userId = seedUser("ali");
        repo.save(bill(userId, "Mid", 15, true));
        repo.save(bill(userId, "Late", 28, true));
        repo.save(bill(userId, "Early", 1, true));

        assertThat(repo.findByUserIdOrderByDueDayAsc(userId))
                .extracting(Bill::getName)
                .containsExactly("Early", "Mid", "Late");
    }

    @Test
    void findActiveExcludesArchived() {
        UUID userId = seedUser("baris");
        repo.save(bill(userId, "Active", 5, true));
        repo.save(bill(userId, "Archived", 10, false));

        assertThat(repo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId))
                .extracting(Bill::getName)
                .containsExactly("Active");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ada");
        UUID b = seedUser("bob");
        Bill aOwned = repo.save(bill(a, "X", 1, true));

        assertThat(repo.findByIdAndUserId(aOwned.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(aOwned.getId(), b)).isEmpty();
    }
}
