package com.fintrack.budget;

import com.fintrack.common.entity.MonthlySummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, UUID> {

    List<MonthlySummary> findByUserIdOrderByPeriodDesc(UUID userId);

    Optional<MonthlySummary> findByUserIdAndPeriod(UUID userId, String period);
}
