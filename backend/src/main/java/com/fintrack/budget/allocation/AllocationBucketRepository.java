package com.fintrack.budget.allocation;

import com.fintrack.common.entity.AllocationBucket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AllocationBucketRepository extends JpaRepository<AllocationBucket, UUID> {

    List<AllocationBucket> findByUserIdOrderByOrdinalAsc(UUID userId);

    @Modifying
    @Query("delete from AllocationBucket b where b.userId = :userId")
    void deleteByUserId(UUID userId);
}
