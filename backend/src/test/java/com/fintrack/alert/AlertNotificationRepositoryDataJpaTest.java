package com.fintrack.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.AlertNotification;
import com.fintrack.common.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class AlertNotificationRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired AlertNotificationRepository repo;
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

    private AlertNotification notification(UUID userId, String message, Instant readAt) {
        return AlertNotification.builder().userId(userId).message(message).readAt(readAt).build();
    }

    @Test
    void findByUserIdOrderByCreatedAtDescScopesAndReturnsAll() {
        UUID a = seedUser("ali");
        UUID b = seedUser("ada");
        repo.save(notification(a, "first", null));
        repo.save(notification(a, "second", Instant.now()));
        repo.save(notification(b, "other", null));

        var rows = repo.findByUserIdOrderByCreatedAtDesc(a);

        assertThat(rows).hasSize(2).extracting(AlertNotification::getUserId).containsOnly(a);
    }

    @Test
    void countByUserIdAndReadAtIsNullCountsUnread() {
        UUID userId = seedUser("kemal");
        repo.save(notification(userId, "unread-1", null));
        repo.save(notification(userId, "unread-2", null));
        repo.save(notification(userId, "read", Instant.now()));

        assertThat(repo.countByUserIdAndReadAtIsNull(userId)).isEqualTo(2);
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("anna");
        UUID b = seedUser("bob");
        AlertNotification own = repo.save(notification(a, "hi", null));

        assertThat(repo.findByIdAndUserId(own.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(own.getId(), b)).isEmpty();
    }
}
