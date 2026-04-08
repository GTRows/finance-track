package com.fintrack.auth;

import com.fintrack.common.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Loads user details for Spring Security authentication.
 */
@Service
@RequiredArgsConstructor
public class FinTrackUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new FinTrackUserDetails(user);
    }

    /**
     * Loads user by UUID (used by JWT filter after token validation).
     *
     * @param userId the user's UUID as a string
     * @return the user details
     * @throws UsernameNotFoundException if the user does not exist
     */
    public UserDetails loadUserByUserId(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found by id: " + userId));
        return new FinTrackUserDetails(user);
    }
}
