package com.fintrack.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class PortfolioRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired PortfolioRepository repo;
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
    void findActiveByUserExcludesArchived() {
        UUID userId = seedUser("alice");
        repo.save(Portfolio.builder().userId(userId).name("Main").active(true).build());
        repo.save(Portfolio.builder().userId(userId).name("Old").active(false).build());

        assertThat(repo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .extracting(Portfolio::getName)
                .containsExactly("Main");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ada");
        UUID b = seedUser("bob");
        Portfolio aOwned = repo.save(Portfolio.builder().userId(a).name("A").active(true).build());

        assertThat(repo.findByIdAndUserIdAndActiveTrue(aOwned.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserIdAndActiveTrue(aOwned.getId(), b)).isEmpty();
    }

    @Test
    void countActiveIgnoresArchivedAndOtherUsers() {
        UUID a = seedUser("anna");
        UUID b = seedUser("ben");
        repo.save(Portfolio.builder().userId(a).name("A1").active(true).build());
        repo.save(Portfolio.builder().userId(a).name("A2").active(true).build());
        repo.save(Portfolio.builder().userId(a).name("A3").active(false).build());
        repo.save(Portfolio.builder().userId(b).name("B1").active(true).build());

        assertThat(repo.countByUserIdAndActiveTrue(a)).isEqualTo(2);
        assertThat(repo.countByUserIdAndActiveTrue(b)).isEqualTo(1);
    }

    @Test
    void findByUserIdReturnsActiveAndArchivedForBackup() {
        UUID userId = seedUser("carol");
        repo.save(Portfolio.builder().userId(userId).name("Live").active(true).build());
        repo.save(Portfolio.builder().userId(userId).name("Gone").active(false).build());

        assertThat(repo.findByUserIdOrderByCreatedAtAsc(userId))
                .extracting(Portfolio::getName)
                .containsExactlyInAnyOrder("Live", "Gone");
    }
}
