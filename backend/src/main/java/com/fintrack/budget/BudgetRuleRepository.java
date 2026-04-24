package com.fintrack.budget;

import com.fintrack.common.entity.BudgetRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetRuleRepository extends JpaRepository<BudgetRule, UUID> {

    List<BudgetRule> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<BudgetRule> findByIdAndUserId(UUID id, UUID userId);

    Optional<BudgetRule> findByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
