package com.fintrack.budget.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.AllocationBucket;
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
class AllocationBucketRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired AllocationBucketRepository repo;
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

    private AllocationBucket bucket(UUID userId, String name, int ordinal) {
        return AllocationBucket.builder()
                .userId(userId)
                .name(name)
                .percent(new BigDecimal("25.00"))
                .ordinal(ordinal)
                .build();
    }

    @Test
    void findByUserIdOrdersByOrdinalAsc() {
        UUID userId = seedUser("ali");
        repo.save(bucket(userId, "Investments", 2));
        repo.save(bucket(userId, "Savings", 0));
        repo.save(bucket(userId, "Travel", 1));

        assertThat(repo.findByUserIdOrderByOrdinalAsc(userId))
                .extracting(AllocationBucket::getName)
                .containsExactly("Savings", "Travel", "Investments");
    }

    @Test
    void deleteByUserIdRemovesAll() {
        UUID userId = seedUser("ada");
        repo.save(bucket(userId, "A", 0));
        repo.save(bucket(userId, "B", 1));

        repo.deleteByUserId(userId);

        assertThat(repo.findByUserIdOrderByOrdinalAsc(userId)).isEmpty();
    }

    @Test
    void deleteByUserIdLeavesOtherUsers() {
        UUID a = seedUser("anna");
        UUID b = seedUser("bob");
        repo.save(bucket(a, "A", 0));
        repo.save(bucket(b, "B", 0));

        repo.deleteByUserId(a);

        assertThat(repo.findByUserIdOrderByOrdinalAsc(b)).hasSize(1);
    }
}
