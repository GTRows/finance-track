package com.fintrack.portfolio.allocation;

import com.fintrack.common.entity.PortfolioAllocationTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AllocationTargetRepository extends JpaRepository<PortfolioAllocationTarget, UUID> {

    List<PortfolioAllocationTarget> findByPortfolioId(UUID portfolioId);

    void deleteByPortfolioId(UUID portfolioId);
}
