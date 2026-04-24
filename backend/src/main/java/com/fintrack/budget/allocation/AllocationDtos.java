package com.fintrack.budget.allocation;

import com.fintrack.common.entity.AllocationBucket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class AllocationDtos {

    private AllocationDtos() {}

    public record BucketInput(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal percent,
            UUID categoryId) {}

    public record BucketListRequest(@NotNull @Valid List<BucketInput> buckets) {}

    public record BucketResponse(
            UUID id, String name, BigDecimal percent, UUID categoryId, int ordinal) {
        public static BucketResponse from(AllocationBucket b) {
            return new BucketResponse(
                    b.getId(), b.getName(), b.getPercent(), b.getCategoryId(), b.getOrdinal());
        }
    }

    public record PreviewRequest(
            @NotNull @PositiveOrZero BigDecimal income, @PositiveOrZero BigDecimal obligations) {}

    public record AllocatedBucket(
            String name, UUID categoryId, BigDecimal percent, BigDecimal amount) {}

    public record PreviewResponse(
            BigDecimal income,
            BigDecimal obligations,
            BigDecimal discretionary,
            BigDecimal assigned,
            BigDecimal unassigned,
            List<AllocatedBucket> buckets) {}
}
