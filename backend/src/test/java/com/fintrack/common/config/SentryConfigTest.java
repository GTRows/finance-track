package com.fintrack.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditPiiRedactor;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions.BeforeSendCallback;
import io.sentry.protocol.Message;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Pins the contract of {@link SentryConfig#sentryPiiRedactingBeforeSend} — the {@link
 * BeforeSendCallback} that runs over every Sentry event before transmission. Each test invokes the
 * callback directly with a hand-built {@link SentryEvent} so we exercise the real {@link
 * AuditPiiRedactor} (24-05's denylist) without bringing the Sentry SDK's transport up.
 */
class SentryConfigTest {

    private final SentryConfig config = new SentryConfig();
    private final AuditPiiRedactor redactor = new AuditPiiRedactor();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void beforeSendStripsPiiFromEventMessage() {
        BeforeSendCallback callback = config.sentryPiiRedactingBeforeSend(redactor, null);
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setMessage("Login failed for user@example.com from 192.0.2.1");
        event.setMessage(message);

        SentryEvent result = callback.execute(event, new Hint());

        assertThat(result).isNotNull();
        assertThat(result.getMessage()).isNotNull();
        assertThat(result.getMessage().getMessage())
                .doesNotContain("user@example.com")
                .doesNotContain("192.0.2.1")
                .contains("[REDACTED:email]")
                .contains("[REDACTED:ip]");
    }

    @Test
    void beforeSendStripsPiiFromBreadcrumbs() {
        BeforeSendCallback callback = config.sentryPiiRedactingBeforeSend(redactor, null);
        SentryEvent event = new SentryEvent();
        Breadcrumb first = new Breadcrumb();
        first.setMessage("hit /api/v1/auth from operator@fintrack.local");
        Breadcrumb second = new Breadcrumb();
        second.setMessage("refresh from 10.0.0.1");
        event.setBreadcrumbs(List.of(first, second));

        SentryEvent result = callback.execute(event, new Hint());

        assertThat(result).isNotNull();
        List<Breadcrumb> crumbs = result.getBreadcrumbs();
        assertThat(crumbs).hasSize(2);
        assertThat(crumbs.get(0).getMessage())
                .doesNotContain("operator@fintrack.local")
                .contains("[REDACTED:email]");
        assertThat(crumbs.get(1).getMessage()).doesNotContain("10.0.0.1").contains("[REDACTED:ip]");
    }

    @Test
    void beforeSendAttachesTraceTagsWhenSpanActive() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("abc123");
        when(context.spanId()).thenReturn("def456");
        BeforeSendCallback callback = config.sentryPiiRedactingBeforeSend(redactor, tracer);
        SentryEvent event = new SentryEvent();

        SentryEvent result = callback.execute(event, new Hint());

        assertThat(result).isNotNull();
        assertThat(result.getTag("trace.id")).isEqualTo("abc123");
        assertThat(result.getTag("span.id")).isEqualTo("def456");
    }

    @Test
    void beforeSendNoOpsWhenTracerNull() {
        BeforeSendCallback callback = config.sentryPiiRedactingBeforeSend(redactor, null);
        SentryEvent event = new SentryEvent();

        SentryEvent result = callback.execute(event, new Hint());

        assertThat(result).isNotNull();
        assertThat(result.getTag("trace.id")).isNull();
        assertThat(result.getTag("span.id")).isNull();
    }

    @Test
    void beforeSendAttachesRequestIdFromMdc() {
        BeforeSendCallback callback = config.sentryPiiRedactingBeforeSend(redactor, null);

        MDC.put("requestId", "req-7f3a");
        SentryEvent populated = callback.execute(new SentryEvent(), new Hint());
        assertThat(populated).isNotNull();
        assertThat(populated.getTag("request.id")).isEqualTo("req-7f3a");

        MDC.clear();
        SentryEvent empty = callback.execute(new SentryEvent(), new Hint());
        assertThat(empty).isNotNull();
        assertThat(empty.getTag("request.id")).isNull();
    }
}
