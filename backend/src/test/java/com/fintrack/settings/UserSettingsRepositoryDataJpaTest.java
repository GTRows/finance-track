package com.fintrack.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.User;
import com.fintrack.common.entity.UserSettings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class UserSettingsRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired UserSettingsRepository repo;
    @Autowired UserRepository userRepo;

    @PersistenceContext EntityManager entityManager;

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
    void roundTripsEmergencyFundTargetColumns() {
        UUID userId = seedUser("zoe");
        UserSettings settings =
                UserSettings.builder()
                        .userId(userId)
                        .emergencyFundTargetMonths((short) 9)
                        .emergencyFundAmberFloorMonths((short) 4)
                        .build();
        repo.save(settings);
        entityManager.flush();
        entityManager.clear();

        UserSettings reloaded = repo.findById(userId).orElseThrow();
        assertThat(reloaded.getEmergencyFundTargetMonths()).isEqualTo((short) 9);
        assertThat(reloaded.getEmergencyFundAmberFloorMonths()).isEqualTo((short) 4);
    }

    @Test
    void rejectsTargetMonthsBeyondCheckBound() {
        UUID userId = seedUser("yara");
        repo.save(UserSettings.builder().userId(userId).build());
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(
                        () -> {
                            entityManager
                                    .createNativeQuery(
                                            "UPDATE user_settings SET"
                                                    + " emergency_fund_target_months = 99 WHERE"
                                                    + " user_id = :uid")
                                    .setParameter("uid", userId)
                                    .executeUpdate();
                            entityManager.flush();
                        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
