package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.RefreshToken;
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
class RefreshTokenRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired RefreshTokenRepository repo;
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

    private RefreshToken token(UUID userId, String token, Instant expiresAt, Instant lastUsedAt) {
        return RefreshToken.builder()
                .userId(userId)
                .token(token)
                .expiresAt(expiresAt)
                .lastUsedAt(lastUsedAt)
                .build();
    }

    @Test
    void findByTokenReturnsExactMatch() {
        UUID userId = seedUser("ali");
        repo.save(token(userId, "raw-1", Instant.now().plus(1, ChronoUnit.HOURS), Instant.now()));

        assertThat(repo.findByToken("raw-1")).isPresent();
        assertThat(repo.findByToken("missing")).isEmpty();
    }

    @Test
    void findActiveOrdersByLastUsedDesc() {
        UUID userId = seedUser("ada");
        Instant now = Instant.now();
        repo.save(
                token(
                        userId,
                        "old",
                        now.plus(1, ChronoUnit.HOURS),
                        now.minus(2, ChronoUnit.HOURS)));
        repo.save(
                token(
                        userId,
                        "new",
                        now.plus(1, ChronoUnit.HOURS),
                        now.minus(1, ChronoUnit.MINUTES)));
        repo.save(
                token(
                        userId,
                        "expired",
                        now.minus(1, ChronoUnit.HOURS),
                        now.minus(30, ChronoUnit.MINUTES)));

        var active = repo.findByUserIdAndExpiresAtAfterOrderByLastUsedAtDesc(userId, now);

        assertThat(active).extracting(RefreshToken::getToken).containsExactly("new", "old");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("anna");
        UUID b = seedUser("bob");
        RefreshToken aToken =
                repo.save(token(a, "x", Instant.now().plus(1, ChronoUnit.HOURS), Instant.now()));

        assertThat(repo.findByIdAndUserId(aToken.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(aToken.getId(), b)).isEmpty();
    }

    @Test
    void deleteByUserIdRemovesAll() {
        UUID userId = seedUser("kemal");
        repo.save(token(userId, "1", Instant.now().plus(1, ChronoUnit.HOURS), Instant.now()));
        repo.save(token(userId, "2", Instant.now().plus(1, ChronoUnit.HOURS), Instant.now()));

        repo.deleteByUserId(userId);

        assertThat(repo.findByUserIdAndExpiresAtAfterOrderByLastUsedAtDesc(userId, Instant.now()))
                .isEmpty();
    }

    @Test
    void deleteByUserIdExceptKeepsOneRow() {
        UUID userId = seedUser("ozge");
        RefreshToken keep =
                repo.save(
                        token(
                                userId,
                                "keep",
                                Instant.now().plus(1, ChronoUnit.HOURS),
                                Instant.now()));
        repo.save(token(userId, "drop", Instant.now().plus(1, ChronoUnit.HOURS), Instant.now()));

        int removed = repo.deleteByUserIdExcept(userId, keep.getId());

        assertThat(removed).isEqualTo(1);
        assertThat(repo.findByToken("keep")).isPresent();
        assertThat(repo.findByToken("drop")).isEmpty();
    }

    @Test
    void deleteByTokenRemovesSingle() {
        UUID userId = seedUser("yusuf");
        repo.save(token(userId, "x", Instant.now().plus(1, ChronoUnit.HOURS), Instant.now()));

        repo.deleteByToken("x");

        assertThat(repo.findByToken("x")).isEmpty();
    }

    @Test
    void deleteExpiredRemovesPastCutoff() {
        UUID userId = seedUser("baris");
        Instant now = Instant.now();
        repo.save(token(userId, "expired", now.minus(1, ChronoUnit.HOURS), now));
        repo.save(token(userId, "alive", now.plus(1, ChronoUnit.HOURS), now));

        int removed = repo.deleteExpired(now);

        assertThat(removed).isEqualTo(1);
        assertThat(repo.findByToken("alive")).isPresent();
    }
}
