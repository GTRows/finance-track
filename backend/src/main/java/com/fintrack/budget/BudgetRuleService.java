package com.fintrack.budget;

import com.fintrack.alert.AlertNotificationRepository;
import com.fintrack.budget.dto.BudgetRuleResponse;
import com.fintrack.budget.dto.CreateBudgetRuleRequest;
import com.fintrack.common.entity.AlertNotification;
import com.fintrack.common.entity.BudgetRule;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.metrics.BusinessMetrics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetRuleService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BudgetRuleRepository ruleRepo;
    private final ExpenseCategoryRepository expenseRepo;
    private final TransactionRepository txnRepo;
    private final AlertNotificationRepository notificationRepo;
    private final BusinessMetrics businessMetrics;

    @Transactional(readOnly = true)
    public List<BudgetRuleResponse> listForUser(UUID userId) {
        List<BudgetRule> rules = ruleRepo.findByUserIdOrderByCreatedAtDesc(userId);
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<UUID, ExpenseCategory> catMap =
                expenseRepo.findByUserIdOrderByNameAsc(userId).stream()
                        .collect(java.util.stream.Collectors.toMap(ExpenseCategory::getId, c -> c));

        YearMonth ym = YearMonth.now();
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Map<UUID, BigDecimal> spendByCategory =
                txnRepo
                        .findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                                userId,
                                from,
                                to,
                                org.springframework.data.domain.Pageable.unpaged())
                        .getContent()
                        .stream()
                        .filter(
                                t ->
                                        t.getTxnType() == BudgetTransaction.TxnType.EXPENSE
                                                && t.getCategoryId() != null)
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        BudgetTransaction::getCategoryId,
                                        java.util.stream.Collectors.reducing(
                                                BigDecimal.ZERO,
                                                BudgetTransaction::getAmount,
                                                BigDecimal::add)));

        return rules.stream()
                .map(
                        r -> {
                            ExpenseCategory cat = catMap.get(r.getCategoryId());
                            String name = cat != null ? cat.getName() : "Unknown";
                            String color = cat != null ? cat.getColor() : null;
                            BigDecimal spent =
                                    spendByCategory.getOrDefault(
                                            r.getCategoryId(), BigDecimal.ZERO);
                            BigDecimal pct =
                                    r.getMonthlyLimitTry().signum() > 0
                                            ? spent.divide(
                                                            r.getMonthlyLimitTry(),
                                                            4,
                                                            RoundingMode.HALF_UP)
                                                    .multiply(BigDecimal.valueOf(100))
                                            : BigDecimal.ZERO;
                            return BudgetRuleResponse.from(r, name, color, spent, pct);
                        })
                .toList();
    }

    @Transactional
    public BudgetRuleResponse create(UUID userId, CreateBudgetRuleRequest req) {
        ExpenseCategory cat =
                expenseRepo
                        .findByIdAndUserId(req.categoryId(), userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        BudgetRule rule =
                ruleRepo.findByUserIdAndCategoryId(userId, req.categoryId())
                        .orElseGet(
                                () ->
                                        BudgetRule.builder()
                                                .userId(userId)
                                                .categoryId(req.categoryId())
                                                .build());
        rule.setMonthlyLimitTry(req.monthlyLimitTry());
        rule.setActive(true);
        rule = ruleRepo.save(rule);

        log.info(
                "Budget rule saved: id={} category={} limit={}",
                rule.getId(),
                cat.getName(),
                rule.getMonthlyLimitTry());
        return BudgetRuleResponse.from(
                rule, cat.getName(), cat.getColor(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public void delete(UUID userId, UUID ruleId) {
        BudgetRule rule =
                ruleRepo.findByIdAndUserId(ruleId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Rule not found"));
        ruleRepo.delete(rule);
        log.info("Budget rule deleted: id={}", ruleId);
    }

    /**
     * After an expense transaction is saved, check whether its category has a rule whose monthly
     * limit has just been crossed for the current period. Emit at most one notification per rule
     * per period.
     */
    @Transactional
    public void evaluateForTransaction(UUID userId, BudgetTransaction txn) {
        if (txn.getTxnType() != BudgetTransaction.TxnType.EXPENSE) return;
        if (txn.getCategoryId() == null) return;

        Optional<BudgetRule> ruleOpt =
                ruleRepo.findByUserIdAndCategoryId(userId, txn.getCategoryId());
        if (ruleOpt.isEmpty()) return;
        BudgetRule rule = ruleOpt.get();
        if (!rule.isActive()) return;

        YearMonth ym = YearMonth.from(txn.getTxnDate());
        String period = ym.format(PERIOD_FMT);

        if (period.equals(rule.getLastAlertedPeriod())) return;

        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        BigDecimal totalSpent =
                txnRepo.sumByUserIdAndCategoryAndDateRange(userId, txn.getCategoryId(), from, to);
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;

        if (totalSpent.compareTo(rule.getMonthlyLimitTry()) < 0) return;

        ExpenseCategory cat = expenseRepo.findById(rule.getCategoryId()).orElse(null);
        String catName = cat != null ? cat.getName() : "category";
        String message =
                "Budget exceeded for "
                        + catName
                        + " in "
                        + period
                        + ": spent "
                        + totalSpent.toPlainString()
                        + " TRY of "
                        + rule.getMonthlyLimitTry().toPlainString()
                        + " TRY limit.";

        AlertNotification notification =
                AlertNotification.builder()
                        .userId(userId)
                        .message(message)
                        .sourceType(AlertNotification.SourceType.BUDGET_RULE)
                        .sourceId(rule.getId())
                        .build();
        notificationRepo.save(notification);
        businessMetrics.recordAlertFired("budget");

        rule.setLastAlertedPeriod(period);
        log.info(
                "Budget rule alert raised: rule={} period={} spent={} limit={}",
                rule.getId(),
                period,
                totalSpent,
                rule.getMonthlyLimitTry());
    }
}
