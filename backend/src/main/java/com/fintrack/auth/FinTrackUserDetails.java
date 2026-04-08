package com.fintrack.auth;

import com.fintrack.common.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security UserDetails wrapper around our User entity.
 * Accessible via @AuthenticationPrincipal in controllers.
 */
public class FinTrackUserDetails implements UserDetails {

    private final UUID id;
    private final String username;
    private final String password;
    private final String role;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public FinTrackUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.role = user.getRole().name();
        this.active = user.isActive();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    /** Returns the user's UUID primary key. */
    public UUID getId() {
        return id;
    }

    /** Returns the user's role as a string (USER or ADMIN). */
    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
