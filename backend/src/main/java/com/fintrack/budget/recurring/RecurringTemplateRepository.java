package com.fintrack.budget.recurring;

import com.fintrack.common.entity.RecurringTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringTemplateRepository extends JpaRepository<RecurringTemplate, UUID> {

    List<RecurringTemplate> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<RecurringTemplate> findByIdAndUserId(UUID id, UUID userId);

    List<RecurringTemplate> findByActiveTrue();
}
