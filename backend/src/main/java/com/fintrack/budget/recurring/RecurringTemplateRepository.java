package com.fintrack.budget.recurring;

import com.fintrack.common.entity.RecurringTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecurringTemplateRepository extends JpaRepository<RecurringTemplate, UUID> {

    List<RecurringTemplate> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<RecurringTemplate> findByIdAndUserId(UUID id, UUID userId);

    List<RecurringTemplate> findByActiveTrue();
}
