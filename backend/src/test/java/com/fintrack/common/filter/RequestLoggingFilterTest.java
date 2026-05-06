package com.fintrack.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Pins the {@link RequestLoggingFilter} contract: every HTTP request gets a short {@code requestId}
 * written to MDC and to the {@code X-Request-Id} response header, MDC is cleared after the request
 * completes, and when a Micrometer {@link Tracer} is wired the current span's traceId/spanId are
 * also exposed via MDC for log-to-trace correlation.
 */
class RequestLoggingFilterTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void requestIdIsAssignedToMdcAndResponseHeader() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        FilterChain chain =
                (req, res) -> capturedRequestId.set(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY));

        filter.doFilter(request, response, chain);

        assertThat(capturedRequestId.get()).isNotNull().hasSize(8);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(capturedRequestId.get());
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY)).isNull();
    }

    @Test
    void mdcIsClearedAfterRequestEvenWhenChainThrows() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain =
                (req, res) -> {
                    throw new RuntimeException("boom");
                };

        try {
            filter.doFilter(request, response, chain);
        } catch (RuntimeException expected) {
            // swallowed -- only the MDC cleanup is being verified
        }

        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void traceContextSetsMdcWhenTracerIsPresent() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("abcd1234abcd1234abcd1234abcd1234");
        when(context.spanId()).thenReturn("ef567890ef567890");

        RequestLoggingFilter filter = new RequestLoggingFilter(tracer);
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/prices/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedTrace = new AtomicReference<>();
        AtomicReference<String> capturedSpan = new AtomicReference<>();
        FilterChain chain =
                (req, res) -> {
                    capturedTrace.set(MDC.get("traceId"));
                    capturedSpan.set(MDC.get("spanId"));
                };

        filter.doFilter(request, response, chain);

        assertThat(capturedTrace.get()).isEqualTo("abcd1234abcd1234abcd1234abcd1234");
        assertThat(capturedSpan.get()).isEqualTo("ef567890ef567890");
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void traceMdcIsSkippedWhenTracerHasNoCurrentSpan() throws Exception {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        RequestLoggingFilter filter = new RequestLoggingFilter(tracer);
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        HttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedTrace = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedTrace.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        assertThat(capturedTrace.get()).isNull();
    }
}
