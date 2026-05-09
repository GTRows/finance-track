package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.TotpRecoveryCode;
import com.fintrack.common.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class TotpRecoveryCodeRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired TotpRecoveryCodeRepository repo;
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

    private TotpRecoveryCode code(UUID userId, String hash, Instant consumedAt) {
        return TotpRecoveryCode.builder()
                .userId(userId)
                .codeHash(hash)
                .consumedAt(consumedAt)
                .build();
    }

    @Test
    void findActiveExcludesConsumed() {
        UUID userId = seedUser("ali");
        repo.save(code(userId, "live-1", null));
        repo.save(code(userId, "live-2", null));
        repo.save(code(userId, "used", Instant.now()));

        assertThat(repo.findActiveByUserId(userId)).hasSize(2);
    }

    @Test
    void countByUserIdAndConsumedAtIsNullCountsActive() {
        UUID userId = seedUser("ada");
        repo.save(code(userId, "a", null));
        repo.save(code(userId, "b", Instant.now()));

        assertThat(repo.countByUserIdAndConsumedAtIsNull(userId)).isEqualTo(1);
    }

    @Test
    void deleteByUserIdRemovesAll() {
        UUID userId = seedUser("kemal");
        repo.save(code(userId, "x", null));
        repo.save(code(userId, "y", null));

        repo.deleteByUserId(userId);

        assertThat(repo.findActiveByUserId(userId)).isEmpty();
    }

    @Test
    void findActiveReturnsEmptyForUnknownUser() {
        assertThat(repo.findActiveByUserId(UUID.randomUUID())).isEmpty();
    }
}
