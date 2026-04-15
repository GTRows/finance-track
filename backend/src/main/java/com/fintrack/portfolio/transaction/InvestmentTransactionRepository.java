package com.fintrack.portfolio.transaction;

import com.fintrack.common.entity.InvestmentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestmentTransactionRepository extends JpaRepository<InvestmentTransaction, UUID> {

    List<InvestmentTransaction> findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(UUID portfolioId);

    Optional<InvestmentTransaction> findByIdAndPortfolioId(UUID id, UUID portfolioId);

    List<InvestmentTransaction> findByPortfolioIdInAndNotesStartingWith(List<UUID> portfolioIds, String notesPrefix);
}
