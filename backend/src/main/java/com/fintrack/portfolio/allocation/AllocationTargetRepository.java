package com.fintrack.portfolio.allocation;

import com.fintrack.common.entity.PortfolioAllocationTarget;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AllocationTargetRepository extends JpaRepository<PortfolioAllocationTarget, UUID> {

    List<PortfolioAllocationTarget> findByPortfolioId(UUID portfolioId);

    void deleteByPortfolioId(UUID portfolioId);
}
