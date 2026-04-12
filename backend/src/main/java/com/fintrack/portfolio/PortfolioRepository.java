package com.fintrack.portfolio;

import com.fintrack.common.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link Portfolio} entities.
 */
@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    /** Returns all active portfolios for a user, ordered by creation time. */
    List<Portfolio> findByUserIdAndActiveTrueOrderByCreatedAtAsc(UUID userId);

    /** Finds an active portfolio by id and user id (ownership check). */
    Optional<Portfolio> findByIdAndUserIdAndActiveTrue(UUID id, UUID userId);

    /** Counts active portfolios for a user. */
    long countByUserIdAndActiveTrue(UUID userId);

    /** Returns all active portfolios across users (used by the snapshot scheduler). */
    List<Portfolio> findAllByActiveTrue();
}
