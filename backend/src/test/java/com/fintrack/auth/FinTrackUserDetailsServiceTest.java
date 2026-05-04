package com.fintrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class FinTrackUserDetailsServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks FinTrackUserDetailsService service;

    private User user(UUID id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .password("hashed")
                .role(User.Role.USER)
                .active(true)
                .build();
    }

    @Test
    void loadByUsernameReturnsDetailsWrappingFoundUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(id, "alice")));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details).isInstanceOf(FinTrackUserDetails.class);
        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(((FinTrackUserDetails) details).getId()).isEqualTo(id);
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    void loadByUsernameThrowsWhenMissingWithUsernameInMessage() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void loadByUserIdReturnsDetailsWrappingFoundUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, "bob")));

        UserDetails details = service.loadUserByUserId(id.toString());

        assertThat(details).isInstanceOf(FinTrackUserDetails.class);
        assertThat(details.getUsername()).isEqualTo("bob");
        assertThat(((FinTrackUserDetails) details).getId()).isEqualTo(id);
    }

    @Test
    void loadByUserIdThrowsWhenMissingWithIdInMessage() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUserId(id.toString()))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void loadByUserIdRejectsNonUuidInput() {
        assertThatThrownBy(() -> service.loadUserByUserId("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
