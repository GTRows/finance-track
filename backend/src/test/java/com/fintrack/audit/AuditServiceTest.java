package com.fintrack.audit;

import com.fintrack.common.entity.AuditLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditLogRepository repository;

    @InjectMocks AuditService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("User-Agent", "unit-agent/1.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordSavesEntryWithRequestMetadata() {
        service.record("LOGIN", AuditLog.Status.SUCCESS, userId, "ali", "detail");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("LOGIN");
        assertThat(saved.getStatus()).isEqualTo(AuditLog.Status.SUCCESS);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getUsername()).isEqualTo("ali");
        assertThat(saved.getDetail()).isEqualTo("detail");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.5");
        assertThat(saved.getUserAgent()).isEqualTo("unit-agent/1.0");
    }

    @Test
    void recordTruncatesUserAgentAndDetail() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("User-Agent", "a".repeat(400));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String longDetail = "b".repeat(800);

        service.record("ACT", AuditLog.Status.SUCCESS, userId, "ali", longDetail);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(255);
        assertThat(captor.getValue().getDetail()).hasSize(500);
    }

    @Test
    void recordPrefersXForwardedForWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.record("ACT", AuditLog.Status.SUCCESS, userId, "ali", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("1.2.3.4");
    }

    @Test
    void recordWorksOutsideOfRequestContext() {
        RequestContextHolder.resetRequestAttributes();

        service.record("BACKGROUND", AuditLog.Status.SUCCESS, userId, "ali", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isNull();
        assertThat(captor.getValue().getUserAgent()).isNull();
    }

    @Test
    void recordPassesNullDetailThroughUntouched() {
        service.record("ACT", AuditLog.Status.FAILURE, null, "ali", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    void recordSwallowsRepositoryFailureAndStillLogs() {
        doThrow(new RuntimeException("boom")).when(repository).save(any(AuditLog.class));

        // Should not propagate the exception.
        service.record("ACT", AuditLog.Status.SUCCESS, userId, "ali", "detail");

        verify(repository).save(any(AuditLog.class));
    }

    @Test
    void successNoDetailRecordsSuccessWithNullDetail() {
        service.success("LOGIN", userId, "ali");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AuditLog.Status.SUCCESS);
        assertThat(captor.getValue().getDetail()).isNull();
    }

    @Test
    void successWithDetailForwardsDetail() {
        service.success("LOGIN", userId, "ali", "ok");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isEqualTo("ok");
    }

    @Test
    void failureWithoutUserIdPersistsNullUserId() {
        service.failure("LOGIN", "ali", "bad pw");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(AuditLog.Status.FAILURE);
    }

    @Test
    void failureWithUserIdPersistsId() {
        service.failure("LOGIN", userId, "ali", "bad pw");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getDetail()).isEqualTo("bad pw");
    }

    @Test
    void recordHandlesNullUsernameWithoutCrashing() {
        service.record("ACT", AuditLog.Status.FAILURE, null, null, "d");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isNull();
    }

    @Test
    void recordDoesNotRetryOnSuccess() {
        service.record("ACT", AuditLog.Status.SUCCESS, userId, "ali", "d");

        verify(repository, org.mockito.Mockito.times(1)).save(any());
        verify(repository, never()).delete(any());
    }
}
