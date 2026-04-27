package com.fintrack.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class IncomeCategoryRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired IncomeCategoryRepository repo;
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

    @Test
    void findByUserIdReturnsCategoriesOrderedByName() {
        UUID userId = seedUser("ali");
        repo.save(IncomeCategory.builder().userId(userId).name("Salary").build());
        repo.save(IncomeCategory.builder().userId(userId).name("Bonus").build());
        repo.save(IncomeCategory.builder().userId(userId).name("Rental").build());

        assertThat(repo.findByUserIdOrderByNameAsc(userId))
                .extracting(IncomeCategory::getName)
                .containsExactly("Bonus", "Rental", "Salary");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ada");
        UUID b = seedUser("bob");
        IncomeCategory aCat = repo.save(IncomeCategory.builder().userId(a).name("Salary").build());

        assertThat(repo.findByIdAndUserId(aCat.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(aCat.getId(), b)).isEmpty();
    }
}
