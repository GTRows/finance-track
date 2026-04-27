package com.fintrack.tag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Tag;
import com.fintrack.common.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class TagRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired TagRepository repo;
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
    void findByUserIdReturnsTagsOrderedByName() {
        UUID userId = seedUser("ali");
        repo.save(Tag.builder().userId(userId).name("Travel").build());
        repo.save(Tag.builder().userId(userId).name("Apple").build());
        repo.save(Tag.builder().userId(userId).name("Gas").build());

        assertThat(repo.findByUserIdOrderByNameAsc(userId))
                .extracting(Tag::getName)
                .containsExactly("Apple", "Gas", "Travel");
    }

    @Test
    void findByIdAndUserIdEnforcesOwnership() {
        UUID a = seedUser("ada");
        UUID b = seedUser("bob");
        Tag t = repo.save(Tag.builder().userId(a).name("Travel").build());

        assertThat(repo.findByIdAndUserId(t.getId(), a)).isPresent();
        assertThat(repo.findByIdAndUserId(t.getId(), b)).isEmpty();
    }

    @Test
    void findByUserIdAndNameLooksUpExactMatch() {
        UUID userId = seedUser("kemal");
        repo.save(Tag.builder().userId(userId).name("Groceries").build());

        assertThat(repo.findByUserIdAndName(userId, "Groceries")).isPresent();
        assertThat(repo.findByUserIdAndName(userId, "groceries")).isEmpty();
    }

    @Test
    void findAllByIdInAndUserIdScopesToOwner() {
        UUID a = seedUser("anne");
        UUID b = seedUser("brad");
        Tag aTag = repo.save(Tag.builder().userId(a).name("X").build());
        Tag bTag = repo.save(Tag.builder().userId(b).name("Y").build());

        assertThat(repo.findAllByIdInAndUserId(List.of(aTag.getId(), bTag.getId()), a))
                .extracting(Tag::getId)
                .containsExactly(aTag.getId());
    }
}
