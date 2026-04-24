package com.fintrack.budget;

import com.fintrack.common.entity.BudgetTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<BudgetTransaction, UUID> {

    Optional<BudgetTransaction> findByIdAndUserId(UUID id, UUID userId);

    List<BudgetTransaction> findByIdInAndUserId(Collection<UUID> ids, UUID userId);

    List<BudgetTransaction> findByUserIdOrderByTxnDateAsc(UUID userId);

    Page<BudgetTransaction> findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
            UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    Page<BudgetTransaction> findByUserIdAndTxnTypeAndTxnDateBetweenOrderByTxnDateDesc(
            UUID userId,
            BudgetTransaction.TxnType txnType,
            LocalDate from,
            LocalDate to,
            Pageable pageable);

    @Query(
            "SELECT t FROM BudgetTransaction t WHERE t.userId = :userId AND t.txnDate BETWEEN :from"
                    + " AND :to AND t.id IN (SELECT tt.transactionId FROM TransactionTag tt WHERE"
                    + " tt.tagId = :tagId) ORDER BY t.txnDate DESC")
    Page<BudgetTransaction> findByUserIdAndTagIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("tagId") UUID tagId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query(
            "SELECT t FROM BudgetTransaction t WHERE t.userId = :userId AND t.txnType = :txnType"
                + " AND t.txnDate BETWEEN :from AND :to AND t.id IN (SELECT tt.transactionId FROM"
                + " TransactionTag tt WHERE tt.tagId = :tagId) ORDER BY t.txnDate DESC")
    Page<BudgetTransaction> findByUserIdAndTypeAndTagIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("txnType") BudgetTransaction.TxnType txnType,
            @Param("tagId") UUID tagId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM BudgetTransaction t WHERE t.userId = :userId"
                    + " AND t.txnType = :txnType AND t.txnDate BETWEEN :from AND :to")
    BigDecimal sumByUserIdAndTypeAndDateRange(
            UUID userId, BudgetTransaction.TxnType txnType, LocalDate from, LocalDate to);

    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM BudgetTransaction t WHERE t.userId = :userId"
                    + " AND t.categoryId = :categoryId AND t.txnType ="
                    + " com.fintrack.common.entity.BudgetTransaction.TxnType.EXPENSE AND t.txnDate"
                    + " BETWEEN :from AND :to")
    BigDecimal sumByUserIdAndCategoryAndDateRange(
            UUID userId, UUID categoryId, LocalDate from, LocalDate to);

    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM BudgetTransaction t "
                    + "WHERE t.txnType = :txnType AND t.txnDate BETWEEN :from AND :to")
    BigDecimal sumByTypeAndDateRange(
            BudgetTransaction.TxnType txnType, LocalDate from, LocalDate to);

    long countByTxnDate(LocalDate txnDate);
}
