package com.fintrack.auth;

import com.fintrack.auth.dto.*;
import com.fintrack.common.entity.RefreshToken;
import com.fintrack.common.entity.User;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.settings.UserSettingsRepository;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles user registration, login, token refresh, and profile retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    /**
     * Registers a new user account and returns token pair.
     *
     * @param request the registration data
     * @return auth response with tokens and user profile
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessRuleException("Username already exists", "USERNAME_TAKEN");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email already registered", "EMAIL_TAKEN");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(User.Role.USER)
                .build();
        user = userRepository.save(user);

        UserSettings settings = UserSettings.builder()
                .userId(user.getId())
                .build();
        userSettingsRepository.save(settings);

        log.info("User registered: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param request the login credentials
     * @return auth response with tokens and user profile
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        log.info("User logged in: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    /**
     * Refreshes an expired access token using a valid refresh token.
     * Implements token rotation: old refresh token is invalidated.
     *
     * @param request contains the current refresh token
     * @return auth response with new token pair
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken existing = refreshTokenService.validate(request.refreshToken());
        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String newRefreshToken = refreshTokenService.rotate(request.refreshToken(), user.getId());
        String accessToken = jwtUtil.generateAccessToken(
                user.getId().toString(), user.getUsername(), user.getRole().name());

        log.debug("Token refreshed for user: {}", user.getUsername());
        return new AuthResponse(accessToken, newRefreshToken, jwtUtil.getAccessExpirySeconds(), toProfile(user));
    }

    /**
     * Logs out a user by revoking their refresh token.
     *
     * @param refreshToken the token to revoke
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        log.debug("User logged out");
    }

    /**
     * Returns the profile of the given user.
     *
     * @param userId the user's UUID as string
     * @return user profile data
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toProfile(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getId().toString(), user.getUsername(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());
        return new AuthResponse(accessToken, refreshToken, jwtUtil.getAccessExpirySeconds(), toProfile(user));
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }
}
