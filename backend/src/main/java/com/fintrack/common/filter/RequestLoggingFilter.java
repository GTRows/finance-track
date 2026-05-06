package com.fintrack.common.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a unique requestId to every HTTP request via MDC. Logs method, path, status, duration,
 * userId, and client IP. The requestId is included in all log lines and in error responses. When a
 * Micrometer {@link Tracer} is on the classpath, the filter also exposes the current span's {@code
 * traceId} and {@code spanId} via MDC so existing log lines correlate one-to-one with the traces
 * emitted by the OTLP exporter (Phase 26 plan 01).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int SHORT_UUID_LENGTH = 8;

    private final Tracer tracer;

    public RequestLoggingFilter(@Nullable Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, SHORT_UUID_LENGTH);
        long startTime = System.currentTimeMillis();

        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        populateTraceMdc();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            String userId = resolveUserId();
            if (userId != null) {
                MDC.put(USER_ID_KEY, userId);
            }

            log.info(
                    "HTTP {} {} {} {}ms ip={} user={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    getClientIp(request),
                    userId != null ? userId : "anonymous");

            MDC.clear();
        }
    }

    private void populateTraceMdc() {
        if (tracer == null) {
            return;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return;
        }
        MDC.put(TRACE_ID_KEY, span.context().traceId());
        MDC.put(SPAN_ID_KEY, span.context().spanId());
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal()
                        instanceof com.fintrack.auth.FinTrackUserDetails userDetails) {
            return userDetails.getId().toString();
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
