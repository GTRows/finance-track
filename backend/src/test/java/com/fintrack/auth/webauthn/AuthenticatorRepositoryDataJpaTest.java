package com.fintrack.auth.webauthn;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.PostgresDataJpaTest;
import com.fintrack.common.entity.Authenticator;
import com.fintrack.common.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresDataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class AuthenticatorRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired AuthenticatorRepository repo;
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

    private Authenticator authenticator(UUID userId, String label, byte[] credentialId) {
        return Authenticator.builder()
                .userId(userId)
                .credentialId(credentialId)
                .publicKeyCose(("cose-" + label).getBytes())
                .signCount(0L)
                .attestationFmt("none")
                .name(label)
                .build();
    }

    @Test
    void findByUserIdOrderByCreatedAtDescReturnsNewestFirst() throws InterruptedException {
        UUID userId = seedUser("ali");
        repo.save(authenticator(userId, "first", "cred-1".getBytes()));
        Thread.sleep(5);
        repo.save(authenticator(userId, "second", "cred-2".getBytes()));

        var found = repo.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(found).extracting(Authenticator::getName).containsExactly("second", "first");
    }

    @Test
    void findByCredentialIdRoundTrips() {
        UUID userId = seedUser("ada");
        byte[] cred = "credential-bytes".getBytes();
        repo.save(authenticator(userId, "key", cred));

        assertThat(repo.findByCredentialId(cred)).isPresent();
        assertThat(repo.findByCredentialId("missing".getBytes())).isEmpty();
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID owner = seedUser("anna");
        UUID stranger = seedUser("bob");
        Authenticator saved = repo.save(authenticator(owner, "yubikey", "anna-cred".getBytes()));

        assertThat(repo.findByIdAndUserId(saved.getId(), owner)).isPresent();
        assertThat(repo.findByIdAndUserId(saved.getId(), stranger)).isEmpty();
    }

    @Test
    void deleteByIdAndUserIdRemovesOnlyMatchingOwner() {
        UUID owner = seedUser("kemal");
        UUID stranger = seedUser("baris");
        Authenticator saved = repo.save(authenticator(owner, "passkey", "kemal-cred".getBytes()));

        repo.deleteByIdAndUserId(saved.getId(), stranger);
        assertThat(repo.findById(saved.getId())).isPresent();

        repo.deleteByIdAndUserId(saved.getId(), owner);
        assertThat(repo.findById(saved.getId())).isEmpty();
    }
}
