package com.fintrack.portfolio.snapshot;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotSchedulerTest {

    @Mock SnapshotService snapshotService;

    @InjectMocks SnapshotScheduler scheduler;

    @Test
    void midnightRunDelegatesToCaptureDaily() {
        when(snapshotService.captureDaily())
                .thenReturn(new SnapshotService.CaptureResult(LocalDate.now(), 1, 0));

        scheduler.captureAtMidnight();

        verify(snapshotService).captureDaily();
    }

    @Test
    void midnightRunSwallowsServiceException() {
        when(snapshotService.captureDaily()).thenThrow(new RuntimeException("db down"));

        scheduler.captureAtMidnight();

        verify(snapshotService).captureDaily();
    }

    @Test
    void startupRunDelegatesToCaptureDaily() {
        when(snapshotService.captureDaily())
                .thenReturn(new SnapshotService.CaptureResult(LocalDate.now(), 0, 2));

        scheduler.onStartup();

        verify(snapshotService).captureDaily();
    }

    @Test
    void startupRunSwallowsServiceException() {
        when(snapshotService.captureDaily()).thenThrow(new RuntimeException("db down"));

        scheduler.onStartup();

        verify(snapshotService).captureDaily();
    }
}
