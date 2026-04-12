package com.fintrack.budget;

import com.fintrack.common.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    List<ExpenseCategory> findByUserIdOrderByNameAsc(UUID userId);

    Optional<ExpenseCategory> findByIdAndUserId(UUID id, UUID userId);
}
