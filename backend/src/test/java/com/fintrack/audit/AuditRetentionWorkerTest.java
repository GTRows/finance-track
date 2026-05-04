package com.fintrack.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditRetentionWorkerTest {

    @Mock AuditLogRepository repository;
    @Mock AuditService auditService;

    private AuditRetentionWorker worker(AuditRetentionProperties props) {
        return new AuditRetentionWorker(repository, props, auditService);
    }

    @Test
    void disabledConfigSkipsPrune() {
        worker(new AuditRetentionProperties(90, 1000, false)).prune();

        verifyNoInteractions(repository);
        verifyNoInteractions(auditService);
    }

    @Test
    void loopExitsWhenBatchReturnsZero() {
        AuditRetentionProperties props = new AuditRetentionProperties(90, 1000, true);
        when(repository.deleteOldestBatch(any(Instant.class), eq(1000)))
                .thenReturn(1000, 1000, 250, 0);

        worker(props).prune();

        verify(repository, times(4)).deleteOldestBatch(any(Instant.class), eq(1000));
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditService)
                .success(
                        eq(AuditAction.AUDIT_RETENTION_PRUNED),
                        eq(null),
                        eq("system"),
                        detail.capture());
        assertThat(detail.getValue()).contains("deleted=2250");
    }

    @Test
    void safetyCapPreventsRunaway() {
        AuditRetentionProperties props = new AuditRetentionProperties(90, 1000, true);
        when(repository.deleteOldestBatch(any(Instant.class), eq(1000))).thenReturn(1000);

        worker(props).prune();

        verify(repository, times(100)).deleteOldestBatch(any(Instant.class), eq(1000));
    }

    @Test
    void repositoryExceptionPropagates() {
        AuditRetentionProperties props = new AuditRetentionProperties(90, 1000, true);
        when(repository.deleteOldestBatch(any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> worker(props).prune())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
        verify(auditService, never())
                .success(eq(AuditAction.AUDIT_RETENTION_PRUNED), any(), any(), any());
    }
}
