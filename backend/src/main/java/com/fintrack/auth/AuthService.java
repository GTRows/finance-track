package com.fintrack.auth;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
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
import org.springframework.security.core.AuthenticationException;
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
    private final TotpService totpService;
    private final AuditService auditService;

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
        auditService.success(AuditAction.REGISTER, user.getId(), user.getUsername());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            auditService.failure(AuditAction.LOGIN, request.username(), "invalid credentials");
            throw ex;
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isTotpEnabled()) {
            String challenge = jwtUtil.generateTotpChallengeToken(user.getId().toString());
            log.info("TOTP challenge issued for user: {}", user.getUsername());
            auditService.success(AuditAction.TOTP_CHALLENGE_ISSUED, user.getId(), user.getUsername());
            return AuthResponse.challenge(challenge);
        }

        log.info("User logged in: {}", user.getUsername());
        auditService.success(AuditAction.LOGIN, user.getId(), user.getUsername());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse verifyTotp(TotpVerifyRequest request) {
        String userId = jwtUtil.validateTotpChallenge(request.challengeToken());
        if (userId == null) {
            auditService.failure(AuditAction.TOTP_VERIFY, null, "invalid challenge token");
            throw new BusinessRuleException("Invalid or expired challenge", "TOTP_CHALLENGE_INVALID");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            auditService.failure(AuditAction.TOTP_VERIFY, user.getId(), user.getUsername(), "TOTP not enabled");
            throw new BusinessRuleException("TOTP not enabled for this user", "TOTP_NOT_ENABLED");
        }

        if (!totpService.verify(user.getTotpSecret(), request.code())) {
            auditService.failure(AuditAction.TOTP_VERIFY, user.getId(), user.getUsername(), "invalid code");
            throw new BusinessRuleException("Invalid verification code", "TOTP_INVALID");
        }

        log.info("TOTP verified and user logged in: {}", user.getUsername());
        auditService.success(AuditAction.TOTP_VERIFY, user.getId(), user.getUsername());
        auditService.success(AuditAction.LOGIN, user.getId(), user.getUsername(), "with TOTP");
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public TotpStatusResponse totpStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new TotpStatusResponse(user.isTotpEnabled());
    }

    @Transactional
    public TotpSetupResponse totpSetup(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isTotpEnabled()) {
            throw new BusinessRuleException("TOTP already enabled", "TOTP_ALREADY_ENABLED");
        }
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);
        String otpauthUrl = totpService.buildOtpauthUrl(secret, user.getEmail());
        auditService.success(AuditAction.TOTP_SETUP, user.getId(), user.getUsername());
        return new TotpSetupResponse(secret, otpauthUrl);
    }

    @Transactional
    public void totpEnable(UUID userId, TotpEnableRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getTotpSecret() == null) {
            throw new BusinessRuleException("TOTP setup not initiated", "TOTP_NOT_STARTED");
        }
        if (!totpService.verify(user.getTotpSecret(), request.code())) {
            auditService.failure(AuditAction.TOTP_ENABLE, user.getId(), user.getUsername(), "invalid code");
            throw new BusinessRuleException("Invalid verification code", "TOTP_INVALID");
        }
        user.setTotpEnabled(true);
        userRepository.save(user);
        log.info("TOTP enabled for user: {}", user.getUsername());
        auditService.success(AuditAction.TOTP_ENABLE, user.getId(), user.getUsername());
    }

    @Transactional
    public void totpDisable(UUID userId, TotpDisableRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            auditService.failure(AuditAction.TOTP_DISABLE, user.getId(), user.getUsername(), "wrong password");
            throw new BusinessRuleException("Incorrect password", "PASSWORD_INVALID");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        log.info("TOTP disabled for user: {}", user.getUsername());
        auditService.success(AuditAction.TOTP_DISABLE, user.getId(), user.getUsername());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken existing = refreshTokenService.validate(request.refreshToken());
        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String newRefreshToken = refreshTokenService.rotate(request.refreshToken(), user.getId());
        String accessToken = jwtUtil.generateAccessToken(
                user.getId().toString(), user.getUsername(), user.getRole().name());

        log.debug("Token refreshed for user: {}", user.getUsername());
        auditService.success(AuditAction.TOKEN_REFRESH, user.getId(), user.getUsername());
        return AuthResponse.of(accessToken, newRefreshToken, jwtUtil.getAccessExpirySeconds(), toProfile(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        UUID userId = refreshTokenService.peekUserId(refreshToken).orElse(null);
        String username = userId == null
                ? null
                : userRepository.findById(userId).map(User::getUsername).orElse(null);
        refreshTokenService.revoke(refreshToken);
        log.debug("User logged out");
        auditService.success(AuditAction.LOGOUT, userId, username);
    }

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
        return AuthResponse.of(accessToken, refreshToken, jwtUtil.getAccessExpirySeconds(), toProfile(user));
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
