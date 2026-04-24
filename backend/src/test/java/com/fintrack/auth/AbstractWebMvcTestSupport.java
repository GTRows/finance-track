package com.fintrack.auth;

import com.fintrack.common.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Shared helpers for {@code @WebMvcTest} suites that need an authenticated {@link
 * FinTrackUserDetails} principal installed in the SecurityContext.
 */
public abstract class AbstractWebMvcTestSupport {

    protected UUID userId;

    protected final void stubAuthenticatedUser() {
        this.userId = UUID.randomUUID();
        User user =
                User.builder()
                        .id(userId)
                        .username("ali")
                        .email("ali@example.com")
                        .password("pw")
                        .role(User.Role.USER)
                        .build();
        FinTrackUserDetails principal = new FinTrackUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));
    }

    @AfterEach
    final void clearSecurity() {
        SecurityContextHolder.clearContext();
    }
}
