package com.fintrack.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.PostgresDataJpaTest;
import com.fintrack.common.entity.PushSubscription;
import com.fintrack.common.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresDataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class PushSubscriptionRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired PushSubscriptionRepository repo;
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

    private PushSubscription subscription(UUID userId, String endpoint) {
        return PushSubscription.builder()
                .userId(userId)
                .endpoint(endpoint)
                .p256dh("p256dh-key")
                .auth("auth-key")
                .build();
    }

    @Test
    void findByUserIdReturnsScoped() {
        UUID a = seedUser("ali");
        UUID b = seedUser("ada");
        repo.save(subscription(a, "https://push/a/1"));
        repo.save(subscription(a, "https://push/a/2"));
        repo.save(subscription(b, "https://push/b/1"));

        assertThat(repo.findByUserId(a)).hasSize(2);
    }

    @Test
    void findByEndpointReturnsExactMatch() {
        UUID userId = seedUser("kemal");
        repo.save(subscription(userId, "https://push/k/1"));

        assertThat(repo.findByEndpoint("https://push/k/1")).isPresent();
        assertThat(repo.findByEndpoint("https://push/missing")).isEmpty();
    }

    @Test
    void deleteByEndpointRemovesSingle() {
        UUID userId = seedUser("ozge");
        repo.save(subscription(userId, "https://push/o/1"));

        repo.deleteByEndpoint("https://push/o/1");

        assertThat(repo.findByEndpoint("https://push/o/1")).isEmpty();
    }
}
