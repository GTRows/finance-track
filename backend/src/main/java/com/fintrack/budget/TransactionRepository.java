package com.fintrack.budget;

import com.fintrack.common.entity.BudgetTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
                    + " AND t.categoryId = :categoryId AND t.txnType = :txnType AND t.txnDate"
                    + " BETWEEN :from AND :to")
    BigDecimal sumByUserIdCategoryTypeAndDateRange(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("txnType") BudgetTransaction.TxnType txnType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    default BigDecimal sumByUserIdAndCategoryAndDateRange(
            UUID userId, UUID categoryId, LocalDate from, LocalDate to) {
        return sumByUserIdCategoryTypeAndDateRange(
                userId, categoryId, BudgetTransaction.TxnType.EXPENSE, from, to);
    }

    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM BudgetTransaction t "
                    + "WHERE t.txnType = :txnType AND t.txnDate BETWEEN :from AND :to")
    BigDecimal sumByTypeAndDateRange(
            BudgetTransaction.TxnType txnType, LocalDate from, LocalDate to);

    long countByTxnDate(LocalDate txnDate);

    @Query(
            "SELECT t FROM BudgetTransaction t WHERE t.ocrStatus IN :statuses "
                    + "AND t.receiptPath IS NOT NULL "
                    + "AND t.updatedAt < :olderThan "
                    + "ORDER BY t.updatedAt ASC")
    List<BudgetTransaction> findReceiptsForOcr(
            @Param("statuses") Collection<BudgetTransaction.OcrStatus> statuses,
            @Param("olderThan") Instant olderThan,
            Pageable pageable);

    @Query(
            "select t.importFingerprint from BudgetTransaction t "
                    + "where t.accountId = :accountId and t.importFingerprint is not null")
    Set<String> findFingerprintsByAccountId(@Param("accountId") UUID accountId);

    boolean existsByAccountIdAndImportFingerprint(UUID accountId, String importFingerprint);
}
