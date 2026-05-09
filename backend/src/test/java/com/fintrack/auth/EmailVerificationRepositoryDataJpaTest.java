package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.EmailVerification;
import com.fintrack.common.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class EmailVerificationRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired EmailVerificationRepository repo;
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

    private EmailVerification token(UUID userId, String token, Instant expiresAt) {
        return EmailVerification.builder().userId(userId).token(token).expiresAt(expiresAt).build();
    }

    @Test
    void findByTokenReturnsRow() {
        UUID userId = seedUser("ali");
        repo.save(token(userId, "tok-1", Instant.now().plus(1, ChronoUnit.HOURS)));

        assertThat(repo.findByToken("tok-1")).isPresent();
        assertThat(repo.findByToken("missing")).isEmpty();
    }

    @Test
    void consumeOutstandingForUserMarksAllUnconsumed() {
        UUID userId = seedUser("ada");
        repo.save(token(userId, "a", Instant.now().plus(1, ChronoUnit.HOURS)));
        repo.save(token(userId, "b", Instant.now().plus(1, ChronoUnit.HOURS)));
        EmailVerification consumed = token(userId, "c", Instant.now().plus(1, ChronoUnit.HOURS));
        consumed.setConsumedAt(Instant.now());
        repo.save(consumed);

        Instant now = Instant.now();
        int updated = repo.consumeOutstandingForUser(userId, now);

        assertThat(updated).isEqualTo(2);
    }

    @Test
    void deleteExpiredOnlyRemovesPastCutoff() {
        UUID userId = seedUser("kemal");
        repo.save(token(userId, "old", Instant.now().minus(1, ChronoUnit.HOURS)));
        repo.save(token(userId, "fresh", Instant.now().plus(1, ChronoUnit.HOURS)));

        int deleted = repo.deleteExpired(Instant.now());

        assertThat(deleted).isEqualTo(1);
        assertThat(repo.findByToken("fresh")).isPresent();
        assertThat(repo.findByToken("old")).isEmpty();
    }

    @Test
    void consumeOutstandingForUserReturnsZeroWhenNoneOpen() {
        UUID userId = seedUser("yusuf");

        assertThat(repo.consumeOutstandingForUser(userId, Instant.now())).isZero();
    }
}
