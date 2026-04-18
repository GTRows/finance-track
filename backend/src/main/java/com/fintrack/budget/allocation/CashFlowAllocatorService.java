package com.fintrack.budget.allocation;

import com.fintrack.budget.allocation.AllocationDtos.AllocatedBucket;
import com.fintrack.budget.allocation.AllocationDtos.BucketInput;
import com.fintrack.budget.allocation.AllocationDtos.BucketResponse;
import com.fintrack.budget.allocation.AllocationDtos.PreviewRequest;
import com.fintrack.budget.allocation.AllocationDtos.PreviewResponse;
import com.fintrack.common.entity.AllocationBucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps each user's ordered list of allocation buckets and produces a
 * cash-flow preview:
 * <ol>
 *   <li>Discretionary = income - obligations (never negative)</li>
 *   <li>Each bucket = discretionary * percent/100, rounded to 2dp</li>
 *   <li>Unassigned = discretionary - sum(bucket amounts)</li>
 * </ol>
 * The controller is a pure suggestion engine — nothing is booked against the
 * ledger automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashFlowAllocatorService {

    private static final int SCALE = 2;

    private final AllocationBucketRepository bucketRepo;

    @Transactional(readOnly = true)
    public List<BucketResponse> listBuckets(UUID userId) {
        return bucketRepo.findByUserIdOrderByOrdinalAsc(userId).stream()
                .map(BucketResponse::from)
                .toList();
    }

    /**
     * Replace a user's bucket list in one shot. Ordinal is derived from the
     * position of each entry in the input, so the frontend can reorder by
     * resubmitting. Percent totals above 100 are allowed but logged — the user
     * may have obligations they want to over-target; under 100 leaves an
     * unassigned residual.
     */
    @Transactional
    public List<BucketResponse> replaceBuckets(UUID userId, List<BucketInput> inputs) {
        bucketRepo.deleteByUserId(userId);
        bucketRepo.flush();

        BigDecimal total = BigDecimal.ZERO;
        List<AllocationBucket> toSave = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            BucketInput in = inputs.get(i);
            AllocationBucket bucket = AllocationBucket.builder()
                    .userId(userId)
                    .name(in.name().trim())
                    .percent(in.percent())
                    .categoryId(in.categoryId())
                    .ordinal(i)
                    .build();
            toSave.add(bucket);
            total = total.add(in.percent());
        }

        if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
            log.warn("Allocation buckets for {} total {}% (>100%)", userId, total);
        }

        List<AllocationBucket> saved = bucketRepo.saveAll(toSave);
        return saved.stream().map(BucketResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(UUID userId, PreviewRequest req) {
        BigDecimal income = req.income().setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal obligations = req.obligations() == null
                ? BigDecimal.ZERO
                : req.obligations().setScale(SCALE, RoundingMode.HALF_UP);

        BigDecimal discretionary = income.subtract(obligations);
        if (discretionary.signum() < 0) discretionary = BigDecimal.ZERO;

        List<AllocationBucket> buckets = bucketRepo.findByUserIdOrderByOrdinalAsc(userId);
        List<AllocatedBucket> out = new ArrayList<>(buckets.size());
        BigDecimal assigned = BigDecimal.ZERO;
        for (AllocationBucket b : buckets) {
            BigDecimal share = discretionary.multiply(b.getPercent())
                    .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
            assigned = assigned.add(share);
            out.add(new AllocatedBucket(b.getName(), b.getCategoryId(), b.getPercent(), share));
        }

        BigDecimal unassigned = discretionary.subtract(assigned);
        return new PreviewResponse(income, obligations, discretionary, assigned, unassigned, out);
    }
}
