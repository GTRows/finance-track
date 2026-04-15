package com.fintrack.budget;

import com.fintrack.common.entity.BudgetTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<BudgetTransaction, UUID> {

    Optional<BudgetTransaction> findByIdAndUserId(UUID id, UUID userId);

    Page<BudgetTransaction> findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
            UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    Page<BudgetTransaction> findByUserIdAndTxnTypeAndTxnDateBetweenOrderByTxnDateDesc(
            UUID userId, BudgetTransaction.TxnType txnType, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BudgetTransaction t " +
           "WHERE t.userId = :userId AND t.txnType = :txnType AND t.txnDate BETWEEN :from AND :to")
    BigDecimal sumByUserIdAndTypeAndDateRange(UUID userId, BudgetTransaction.TxnType txnType, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BudgetTransaction t " +
           "WHERE t.userId = :userId AND t.categoryId = :categoryId " +
           "AND t.txnType = com.fintrack.common.entity.BudgetTransaction.TxnType.EXPENSE " +
           "AND t.txnDate BETWEEN :from AND :to")
    BigDecimal sumByUserIdAndCategoryAndDateRange(UUID userId, UUID categoryId, LocalDate from, LocalDate to);
}
