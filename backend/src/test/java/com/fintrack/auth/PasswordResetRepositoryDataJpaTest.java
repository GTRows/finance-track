package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.PostgresDataJpaTest;
import com.fintrack.common.entity.PasswordReset;
import com.fintrack.common.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresDataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class PasswordResetRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired PasswordResetRepository repo;
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

    private PasswordReset reset(UUID userId, String token, Instant expiresAt) {
        return PasswordReset.builder().userId(userId).token(token).expiresAt(expiresAt).build();
    }

    @Test
    void findByTokenReturnsRow() {
        UUID userId = seedUser("ali");
        repo.save(reset(userId, "tok-1", Instant.now().plus(1, ChronoUnit.HOURS)));

        assertThat(repo.findByToken("tok-1")).isPresent();
        assertThat(repo.findByToken("missing")).isEmpty();
    }

    @Test
    void consumeOutstandingForUserMarksAllUnconsumed() {
        UUID userId = seedUser("ada");
        repo.save(reset(userId, "a", Instant.now().plus(1, ChronoUnit.HOURS)));
        repo.save(reset(userId, "b", Instant.now().plus(1, ChronoUnit.HOURS)));

        int updated = repo.consumeOutstandingForUser(userId, Instant.now());

        assertThat(updated).isEqualTo(2);
    }

    @Test
    void deleteExpiredOnlyRemovesPastCutoff() {
        UUID userId = seedUser("kemal");
        repo.save(reset(userId, "old", Instant.now().minus(1, ChronoUnit.HOURS)));
        repo.save(reset(userId, "fresh", Instant.now().plus(1, ChronoUnit.HOURS)));

        int deleted = repo.deleteExpired(Instant.now());

        assertThat(deleted).isEqualTo(1);
        assertThat(repo.findByToken("fresh")).isPresent();
    }

    @Test
    void consumeOutstandingForUserReturnsZeroWhenNoneOpen() {
        UUID userId = seedUser("yusuf");

        assertThat(repo.consumeOutstandingForUser(userId, Instant.now())).isZero();
    }
}
