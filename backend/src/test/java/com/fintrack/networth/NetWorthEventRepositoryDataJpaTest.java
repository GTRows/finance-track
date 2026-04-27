package com.fintrack.networth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.NetWorthEvent;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class NetWorthEventRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired NetWorthEventRepository repo;
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

    private NetWorthEvent event(UUID userId, LocalDate date, String label) {
        return NetWorthEvent.builder()
                .userId(userId)
                .eventDate(date)
                .eventType(NetWorthEvent.EventType.MILESTONE)
                .label(label)
                .impactTry(new BigDecimal("0"))
                .build();
    }

    @Test
    void findByUserIdOrdersByEventDateDesc() {
        UUID userId = seedUser("ali");
        repo.save(event(userId, LocalDate.of(2026, 1, 1), "First"));
        repo.save(event(userId, LocalDate.of(2026, 6, 1), "Mid"));
        repo.save(event(userId, LocalDate.of(2026, 12, 1), "Last"));

        assertThat(repo.findByUserIdOrderByEventDateDesc(userId))
                .extracting(NetWorthEvent::getLabel)
                .containsExactly("Last", "Mid", "First");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ada");
        UUID b = seedUser("bob");
        NetWorthEvent aEvent = repo.save(event(a, LocalDate.of(2026, 4, 1), "Bonus"));

        assertThat(repo.findByIdAndUserId(aEvent.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(aEvent.getId(), b)).isEmpty();
    }
}
