package com.fintrack.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Installs a synthetic anonymous-but-permitted authentication when the request targets the receipt
 * download path with a {@code ?token=} query parameter present. This allows the global {@code
 * anyRequest().authenticated()} rule to pass without a JWT; the controller performs the actual
 * cryptographic gate via {@link com.fintrack.budget.receipt.ReceiptUrlSigner}.
 *
 * <p>Must be placed BEFORE {@link JwtAuthFilter} in the security filter chain so the JWT filter
 * does not reject the request before this filter can scaffold auth.
 */
@Component
public class SignedReceiptTokenFilter extends OncePerRequestFilter {

    private static final Pattern RECEIPT_PATH =
            Pattern.compile("^/api/v1/budget/transactions/[^/]+/receipt$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldScaffold(request)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            AnonymousAuthenticationToken auth =
                    new AnonymousAuthenticationToken(
                            "signed-receipt-token",
                            "anonymous",
                            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            auth.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldScaffold(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && RECEIPT_PATH.matcher(request.getRequestURI()).matches()
                && request.getParameter("token") != null;
    }
}
