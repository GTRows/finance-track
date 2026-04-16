package com.fintrack.savings;

import com.fintrack.common.entity.SavingsGoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface SavingsContributionRepository extends JpaRepository<SavingsGoalContribution, UUID> {

    List<SavingsGoalContribution> findByGoalIdOrderByContributionDateDesc(UUID goalId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM SavingsGoalContribution c WHERE c.goalId = :goalId")
    BigDecimal sumByGoalId(@Param("goalId") UUID goalId);

    void deleteByGoalId(UUID goalId);
}
