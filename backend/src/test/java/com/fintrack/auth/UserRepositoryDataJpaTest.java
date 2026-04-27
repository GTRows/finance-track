package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class UserRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired UserRepository repo;

    @Test
    void findByUsernameReturnsTheUser() {
        repo.save(
                User.builder()
                        .username("ali")
                        .email("ali@example.com")
                        .password("bcrypt-hash")
                        .role(User.Role.USER)
                        .build());

        assertThat(repo.findByUsername("ali")).isPresent();
        assertThat(repo.findByUsername("missing")).isEmpty();
    }

    @Test
    void findByEmailReturnsTheUser() {
        repo.save(
                User.builder()
                        .username("ada")
                        .email("ada@example.com")
                        .password("bcrypt-hash")
                        .role(User.Role.USER)
                        .build());

        assertThat(repo.findByEmail("ada@example.com")).isPresent();
        assertThat(repo.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void existsByUsernameAndEmailReflectInsertion() {
        assertThat(repo.existsByUsername("kemal")).isFalse();
        repo.save(
                User.builder()
                        .username("kemal")
                        .email("kemal@example.com")
                        .password("bcrypt-hash")
                        .role(User.Role.USER)
                        .build());
        assertThat(repo.existsByUsername("kemal")).isTrue();
        assertThat(repo.existsByEmail("kemal@example.com")).isTrue();
        assertThat(repo.existsByEmail("kemal@other.com")).isFalse();
    }
}
