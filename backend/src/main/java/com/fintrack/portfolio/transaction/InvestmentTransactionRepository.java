package com.fintrack.portfolio.transaction;

import com.fintrack.common.entity.InvestmentTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentTransactionRepository
        extends JpaRepository<InvestmentTransaction, UUID> {

    List<InvestmentTransaction> findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(UUID portfolioId);

    Optional<InvestmentTransaction> findByIdAndPortfolioId(UUID id, UUID portfolioId);

    List<InvestmentTransaction> findByPortfolioIdInAndNotesStartingWith(
            List<UUID> portfolioIds, String notesPrefix);

    /**
     * Returns transactions of a given type whose txn date is on or before the cutoff, ordered
     * ascending. Used by the portfolio comparison endpoint to roll up realised P&L per snapshot
     * date.
     */
    List<InvestmentTransaction> findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
            UUID portfolioId, InvestmentTransaction.TxnType txnType, LocalDate cutoff);
}
