package com.fintrack.common.config;

import com.fintrack.audit.AuditPiiRedactor;
import io.micrometer.tracing.Tracer;
import io.sentry.SentryOptions.BeforeSendCallback;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * Wires the Sentry SDK's {@link BeforeSendCallback} so every captured event is (a) PII-scrubbed via
 * the existing {@link AuditPiiRedactor} (single redaction policy across audit log and Sentry
 * transport from 24-05) and (b) tagged with {@code trace.id} / {@code span.id} from the active
 * Micrometer span (cross-link to Tempo from 26-01) plus {@code request.id} from the MDC populated
 * by {@code RequestLoggingFilter}. The Sentry Spring Boot starter auto-discovers the bean — no
 * explicit SDK boot call is required.
 */
@Configuration
public class SentryConfig {

    @Bean
    public BeforeSendCallback sentryPiiRedactingBeforeSend(
            AuditPiiRedactor redactor, @Nullable Tracer tracer) {
        return (event, hint) -> {
            if (event.getMessage() != null && event.getMessage().getMessage() != null) {
                event.getMessage().setMessage(redactor.redact(event.getMessage().getMessage()));
            }
            if (event.getBreadcrumbs() != null) {
                event.getBreadcrumbs()
                        .forEach(
                                b -> {
                                    if (b.getMessage() != null) {
                                        b.setMessage(redactor.redact(b.getMessage()));
                                    }
                                });
            }
            if (tracer != null) {
                var span = tracer.currentSpan();
                if (span != null) {
                    event.setTag("trace.id", span.context().traceId());
                    event.setTag("span.id", span.context().spanId());
                }
            }
            String requestId = MDC.get("requestId");
            if (requestId != null && !requestId.isBlank()) {
                event.setTag("request.id", requestId);
            }
            return event;
        };
    }
}
