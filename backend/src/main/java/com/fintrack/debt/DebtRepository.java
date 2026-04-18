package com.fintrack.debt;

import com.fintrack.common.entity.Debt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebtRepository extends JpaRepository<Debt, UUID> {

    @Query("SELECT d FROM Debt d WHERE d.userId = :userId AND d.archivedAt IS NULL " +
           "ORDER BY d.startDate ASC, d.createdAt ASC")
    List<Debt> findActive(@Param("userId") UUID userId);

    Optional<Debt> findByIdAndUserId(UUID id, UUID userId);
}
