package com.fintrack.auth;

import com.fintrack.common.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Trusts an upstream Authelia (or any ForwardAuth-capable proxy) that has already authenticated the
 * user and injected a {@code Remote-User} header.
 *
 * <p>Disabled by default. Activate only when FinTrack sits behind Traefik + Authelia in the
 * homelab. To avoid header-spoofing, the filter only accepts the header when the request originated
 * from a trusted proxy IP configured via {@code fintrack.authelia.trusted-ips}.
 *
 * <p>Runs after the JWT filter: if a valid JWT already populated the context the header is ignored,
 * so direct API clients (mobile, scripts) still work.
 */
@Component
@Slf4j
public class AutheliaForwardAuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Value("${fintrack.authelia.enabled:false}")
    private boolean enabled;

    @Value("${fintrack.authelia.header:Remote-User}")
    private String headerName;

    @Value("${fintrack.authelia.trusted-ips:}")
    private String trustedIpsCsv;

    private Set<String> trustedIps = Set.of();

    public AutheliaForwardAuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void initFilterBean() {
        if (!enabled) return;
        trustedIps =
                Arrays.stream(trustedIpsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        if (trustedIps.isEmpty()) {
            log.warn(
                    "Authelia ForwardAuth enabled with no trusted-ips restriction. Set"
                            + " fintrack.authelia.trusted-ips to your Traefik container IP range to"
                            + " prevent header spoofing.");
        } else {
            log.info("Authelia ForwardAuth enabled. Header={} trusted={}", headerName, trustedIps);
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String principal = request.getHeader(headerName);
        if (!StringUtils.hasText(principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!trustedIps.isEmpty() && !trustedIps.contains(request.getRemoteAddr())) {
            log.warn("Rejecting Authelia header from untrusted remote={}", request.getRemoteAddr());
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> maybeUser =
                userRepository
                        .findByUsername(principal)
                        .or(() -> userRepository.findByEmail(principal));

        if (maybeUser.isEmpty()) {
            log.warn("Authelia ForwardAuth principal not found locally: {}", principal);
            filterChain.doFilter(request, response);
            return;
        }

        FinTrackUserDetails details = new FinTrackUserDetails(maybeUser.get());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
