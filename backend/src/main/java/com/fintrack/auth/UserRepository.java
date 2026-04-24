package com.fintrack.auth;

import com.fintrack.common.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for user account operations. */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Find user by username for login. */
    Optional<User> findByUsername(String username);

    /** Find user by email for duplicate checks. */
    Optional<User> findByEmail(String email);

    /** Check if username is already taken. */
    boolean existsByUsername(String username);

    /** Check if email is already registered. */
    boolean existsByEmail(String email);
}
