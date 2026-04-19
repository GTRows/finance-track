package com.fintrack.budget.allocation;

import com.fintrack.budget.allocation.AllocationDtos.BucketInput;
import com.fintrack.budget.allocation.AllocationDtos.PreviewRequest;
import com.fintrack.budget.allocation.AllocationDtos.PreviewResponse;
import com.fintrack.common.entity.AllocationBucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowAllocatorServiceTest {

    @Mock AllocationBucketRepository bucketRepo;

    @InjectMocks CashFlowAllocatorService service;

    private final UUID userId = UUID.randomUUID();

    private AllocationBucket bucket(String name, BigDecimal percent, int ordinal) {
        return AllocationBucket.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .percent(percent)
                .ordinal(ordinal)
                .build();
    }

    @Test
    void previewWithoutBucketsLeavesAllDiscretionaryUnassigned() {
        when(bucketRepo.findByUserIdOrderByOrdinalAsc(userId)).thenReturn(List.of());

        PreviewResponse res = service.preview(userId,
                new PreviewRequest(new BigDecimal("10000"), new BigDecimal("3000")));

        assertThat(res.income()).isEqualByComparingTo("10000");
        assertThat(res.obligations()).isEqualByComparingTo("3000");
        assertThat(res.discretionary()).isEqualByComparingTo("7000");
        assertThat(res.assigned()).isEqualByComparingTo("0");
        assertThat(res.unassigned()).isEqualByComparingTo("7000");
        assertThat(res.buckets()).isEmpty();
    }

    @Test
    void previewSplitsDiscretionaryAcrossBucketsByPercent() {
        when(bucketRepo.findByUserIdOrderByOrdinalAsc(userId)).thenReturn(List.of(
                bucket("Savings", new BigDecimal("60"), 0),
                bucket("Investments", new BigDecimal("40"), 1)
        ));

        PreviewResponse res = service.preview(userId,
                new PreviewRequest(new BigDecimal("8000"), new BigDecimal("3000")));

        assertThat(res.discretionary()).isEqualByComparingTo("5000");
        assertThat(res.buckets()).hasSize(2);
        assertThat(res.buckets().get(0).amount()).isEqualByComparingTo("3000");
        assertThat(res.buckets().get(1).amount()).isEqualByComparingTo("2000");
        assertThat(res.assigned()).isEqualByComparingTo("5000");
        assertThat(res.unassigned()).isEqualByComparingTo("0");
    }

    @Test
    void previewWithBucketsUnder100LeavesResidualUnassigned() {
        when(bucketRepo.findByUserIdOrderByOrdinalAsc(userId)).thenReturn(List.of(
                bucket("Savings", new BigDecimal("30"), 0),
                bucket("Investments", new BigDecimal("20"), 1)
        ));

        PreviewResponse res = service.preview(userId,
                new PreviewRequest(new BigDecimal("1000"), BigDecimal.ZERO));

        assertThat(res.discretionary()).isEqualByComparingTo("1000");
        assertThat(res.assigned()).isEqualByComparingTo("500");
        assertThat(res.unassigned()).isEqualByComparingTo("500");
    }

    @Test
    void previewClampsDiscretionaryAtZeroWhenObligationsExceedIncome() {
        when(bucketRepo.findByUserIdOrderByOrdinalAsc(userId)).thenReturn(List.of(
                bucket("Savings", new BigDecimal("50"), 0)
        ));

        PreviewResponse res = service.preview(userId,
                new PreviewRequest(new BigDecimal("2000"), new BigDecimal("2500")));

        assertThat(res.discretionary()).isEqualByComparingTo("0");
        assertThat(res.buckets().get(0).amount()).isEqualByComparingTo("0");
        assertThat(res.assigned()).isEqualByComparingTo("0");
        assertThat(res.unassigned()).isEqualByComparingTo("0");
    }

    @Test
    void previewTreatsNullObligationsAsZero() {
        when(bucketRepo.findByUserIdOrderByOrdinalAsc(userId)).thenReturn(List.of());

        PreviewResponse res = service.preview(userId,
                new PreviewRequest(new BigDecimal("1500"), null));

        assertThat(res.obligations()).isEqualByComparingTo("0");
        assertThat(res.discretionary()).isEqualByComparingTo("1500");
        assertThat(res.unassigned()).isEqualByComparingTo("1500");
    }

    @Test
    void replaceBucketsPersistsTrimmedNamesAndAssignsOrdinals() {
        when(bucketRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.replaceBuckets(userId, List.of(
                new BucketInput("  Savings  ", new BigDecimal("40"), null),
                new BucketInput("Investments", new BigDecimal("60"), null)
        ));

        verify(bucketRepo).deleteByUserId(userId);
        verify(bucketRepo).flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationBucket>> captor = ArgumentCaptor.forClass(List.class);
        verify(bucketRepo).saveAll(captor.capture());

        List<AllocationBucket> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getName()).isEqualTo("Savings");
        assertThat(saved.get(0).getOrdinal()).isEqualTo(0);
        assertThat(saved.get(0).getPercent()).isEqualByComparingTo("40");
        assertThat(saved.get(1).getName()).isEqualTo("Investments");
        assertThat(saved.get(1).getOrdinal()).isEqualTo(1);
        assertThat(saved.get(1).getPercent()).isEqualByComparingTo("60");
    }

    @Test
    void replaceBucketsAllowsTotalsOver100() {
        when(bucketRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.replaceBuckets(userId, List.of(
                new BucketInput("Savings", new BigDecimal("60"), null),
                new BucketInput("Investments", new BigDecimal("60"), null)
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AllocationBucket>> captor = ArgumentCaptor.forClass(List.class);
        verify(bucketRepo).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }
}
