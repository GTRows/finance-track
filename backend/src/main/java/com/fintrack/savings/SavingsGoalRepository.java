package com.fintrack.savings;

import com.fintrack.common.entity.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    @Query("SELECT g FROM SavingsGoal g WHERE g.userId = :userId AND g.archivedAt IS NULL " +
           "ORDER BY g.createdAt ASC")
    List<SavingsGoal> findActive(@Param("userId") UUID userId);

    Optional<SavingsGoal> findByIdAndUserId(UUID id, UUID userId);

    List<SavingsGoal> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
