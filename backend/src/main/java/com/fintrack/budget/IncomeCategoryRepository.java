package com.fintrack.budget;

import com.fintrack.common.entity.IncomeCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeCategoryRepository extends JpaRepository<IncomeCategory, UUID> {

    List<IncomeCategory> findByUserIdOrderByNameAsc(UUID userId);

    Optional<IncomeCategory> findByIdAndUserId(UUID id, UUID userId);
}
