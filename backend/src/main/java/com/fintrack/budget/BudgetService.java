package com.fintrack.budget;

import com.fintrack.budget.dto.*;
import com.fintrack.budget.rule.TransactionCategoryRuleService;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.entity.MonthlySummary;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.tag.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TransactionRepository txnRepo;
    private final IncomeCategoryRepository incomeRepo;
    private final ExpenseCategoryRepository expenseRepo;
    private final MonthlySummaryRepository summaryRepo;
    private final BudgetRuleService budgetRuleService;
    private final TransactionCategoryRuleService categoryRuleService;
    private final TagService tagService;

    // -- Transactions --

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(UUID userId, String month,
                                                       BudgetTransaction.TxnType type,
                                                       UUID tagId,
                                                       Pageable pageable) {
        YearMonth ym = YearMonth.parse(month, PERIOD_FMT);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Page<BudgetTransaction> page;
        if (tagId != null && type != null) {
            page = txnRepo.findByUserIdAndTypeAndTagIdAndDateRange(userId, type, tagId, from, to, pageable);
        } else if (tagId != null) {
            page = txnRepo.findByUserIdAndTagIdAndDateRange(userId, tagId, from, to, pageable);
        } else if (type != null) {
            page = txnRepo.findByUserIdAndTxnTypeAndTxnDateBetweenOrderByTxnDateDesc(userId, type, from, to, pageable);
        } else {
            page = txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(userId, from, to, pageable);
        }

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        List<UUID> txnIds = page.getContent().stream().map(BudgetTransaction::getId).toList();
        Map<UUID, List<TransactionResponse.TagRef>> tagLookup = buildTagLookup(userId, txnIds);

        return page.map(t -> toResponse(t, catLookup, tagLookup));
    }

    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest req) {
        UUID categoryId = req.categoryId();
        if (categoryId == null) {
            categoryId = categoryRuleService.matchFor(userId, req.txnType(), req.description()).orElse(null);
        }
        BudgetTransaction txn = BudgetTransaction.builder()
                .userId(userId)
                .txnType(req.txnType())
                .amount(req.amount())
                .categoryId(categoryId)
                .description(req.description())
                .txnDate(req.txnDate())
                .recurring(req.isRecurring())
                .build();
        txn = txnRepo.save(txn);
        log.info("Transaction created: id={} type={} amount={}", txn.getId(), txn.getTxnType(), txn.getAmount());

        List<UUID> ownedTagIds = tagService.resolveOwnedIds(userId, req.tagIds());
        tagService.setTransactionTags(txn.getId(), ownedTagIds);

        try {
            budgetRuleService.evaluateForTransaction(userId, txn);
        } catch (Exception e) {
            log.warn("Budget rule evaluation failed: {}", e.getMessage());
        }

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        Map<UUID, List<TransactionResponse.TagRef>> tagLookup = buildTagLookup(userId, List.of(txn.getId()));
        return toResponse(txn, catLookup, tagLookup);
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID txnId, UpdateTransactionRequest req) {
        BudgetTransaction txn = txnRepo.findByIdAndUserId(txnId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        txn.setTxnType(req.txnType());
        txn.setAmount(req.amount());
        txn.setCategoryId(req.categoryId());
        txn.setDescription(req.description());
        txn.setTxnDate(req.txnDate());
        txn.setRecurring(req.isRecurring());

        List<UUID> ownedTagIds = tagService.resolveOwnedIds(userId, req.tagIds());
        tagService.setTransactionTags(txn.getId(), ownedTagIds);

        try {
            budgetRuleService.evaluateForTransaction(userId, txn);
        } catch (Exception e) {
            log.warn("Budget rule evaluation failed: {}", e.getMessage());
        }

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        Map<UUID, List<TransactionResponse.TagRef>> tagLookup = buildTagLookup(userId, List.of(txn.getId()));
        return toResponse(txn, catLookup, tagLookup);
    }

    @Transactional
    public void delete(UUID userId, UUID txnId) {
        BudgetTransaction txn = txnRepo.findByIdAndUserId(txnId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        txnRepo.delete(txn);
        log.info("Transaction deleted: id={}", txnId);
    }

    @Transactional
    public int bulkDelete(UUID userId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<BudgetTransaction> owned = txnRepo.findByIdInAndUserId(ids, userId);
        if (owned.isEmpty()) return 0;
        txnRepo.deleteAll(owned);
        log.info("Bulk delete transactions: requested={} deleted={}", ids.size(), owned.size());
        return owned.size();
    }

    /**
     * Apply category swap and/or tag add/remove to every selected transaction the
     * user owns. Category changes are skipped for transactions whose txn_type does
     * not match the category's type (validated by looking up the category).
     */
    @Transactional
    public int bulkUpdate(UUID userId,
                          List<UUID> ids,
                          UUID categoryId,
                          boolean clearCategory,
                          List<UUID> addTagIds,
                          List<UUID> removeTagIds) {
        if (ids == null || ids.isEmpty()) return 0;
        List<BudgetTransaction> owned = txnRepo.findByIdInAndUserId(ids, userId);
        if (owned.isEmpty()) return 0;

        // Resolve category once for validation; empty if not set.
        BudgetTransaction.TxnType expectedType = null;
        if (categoryId != null) {
            boolean isExpense = expenseRepo.findByIdAndUserId(categoryId, userId).isPresent();
            boolean isIncome = !isExpense && incomeRepo.findByIdAndUserId(categoryId, userId).isPresent();
            if (!isExpense && !isIncome) {
                throw new ResourceNotFoundException("Category not found");
            }
            expectedType = isExpense ? BudgetTransaction.TxnType.EXPENSE : BudgetTransaction.TxnType.INCOME;
        }

        List<UUID> ownedAddTags = tagService.resolveOwnedIds(userId, addTagIds);
        List<UUID> ownedRemoveTags = tagService.resolveOwnedIds(userId, removeTagIds);

        int affected = 0;
        for (BudgetTransaction txn : owned) {
            boolean changed = false;
            if (clearCategory && categoryId == null) {
                if (txn.getCategoryId() != null) {
                    txn.setCategoryId(null);
                    changed = true;
                }
            } else if (categoryId != null && txn.getTxnType() == expectedType) {
                if (!categoryId.equals(txn.getCategoryId())) {
                    txn.setCategoryId(categoryId);
                    changed = true;
                }
            }
            if (changed) txnRepo.save(txn);

            if (!ownedAddTags.isEmpty() || !ownedRemoveTags.isEmpty()) {
                tagService.mutateTransactionTags(txn.getId(), ownedAddTags, ownedRemoveTags);
                changed = true;
            }

            if (changed) affected++;
        }

        log.info("Bulk update transactions: requested={} affected={} categorySet={} addTags={} removeTags={}",
                ids.size(), affected, categoryId, ownedAddTags.size(), ownedRemoveTags.size());
        return affected;
    }

    // -- Summary --

    @Transactional(readOnly = true)
    public BudgetSummaryResponse summary(UUID userId, String month) {
        YearMonth ym = YearMonth.parse(month, PERIOD_FMT);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        BigDecimal totalIncome = txnRepo.sumByUserIdAndTypeAndDateRange(
                userId, BudgetTransaction.TxnType.INCOME, from, to);
        BigDecimal totalExpense = txnRepo.sumByUserIdAndTypeAndDateRange(
                userId, BudgetTransaction.TxnType.EXPENSE, from, to);
        BigDecimal net = totalIncome.subtract(totalExpense);
        BigDecimal savingsRate = totalIncome.signum() > 0
                ? net.divide(totalIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Page<BudgetTransaction> all = txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(
                userId, from, to, Pageable.unpaged());

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        Map<UUID, BigDecimal> rolloverByCategory = computeRollovers(userId, ym);
        Map<UUID, ExpenseCategory> expenseById = expenseRepo.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, c -> c));

        List<BudgetSummaryResponse.CategoryAmount> incByCat = groupByCategory(
                all.getContent(), BudgetTransaction.TxnType.INCOME, catLookup, totalIncome,
                Map.of(), Map.of());
        List<BudgetSummaryResponse.CategoryAmount> expByCat = groupByCategory(
                all.getContent(), BudgetTransaction.TxnType.EXPENSE, catLookup, totalExpense,
                expenseById, rolloverByCategory);

        return new BudgetSummaryResponse(month, totalIncome, totalExpense, net, savingsRate, incByCat, expByCat);
    }

    /**
     * Walk months from start of year to month before {@code month}; accumulate per-category
     * unused budget for any category flagged {@code rollover_enabled}. Rollover only carries
     * positive balances — overspending in any month resets rollover to zero.
     */
    private Map<UUID, BigDecimal> computeRollovers(UUID userId, YearMonth month) {
        List<ExpenseCategory> rolloverCats = expenseRepo.findByUserIdOrderByNameAsc(userId).stream()
                .filter(ExpenseCategory::isRolloverEnabled)
                .filter(c -> c.getBudgetAmount() != null && c.getBudgetAmount().signum() > 0)
                .toList();
        if (rolloverCats.isEmpty()) return Map.of();

        YearMonth start = YearMonth.of(month.getYear(), 1);
        if (!start.isBefore(month)) return rolloverCats.stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, c -> BigDecimal.ZERO));

        Map<UUID, BigDecimal> carry = new HashMap<>();
        for (ExpenseCategory c : rolloverCats) carry.put(c.getId(), BigDecimal.ZERO);

        YearMonth cursor = start;
        while (cursor.isBefore(month)) {
            LocalDate cFrom = cursor.atDay(1);
            LocalDate cTo = cursor.atEndOfMonth();
            for (ExpenseCategory c : rolloverCats) {
                BigDecimal spent = txnRepo.sumByUserIdAndCategoryAndDateRange(userId, c.getId(), cFrom, cTo);
                BigDecimal effective = c.getBudgetAmount().add(carry.get(c.getId()));
                BigDecimal leftover = effective.subtract(spent);
                carry.put(c.getId(), leftover.signum() > 0 ? leftover : BigDecimal.ZERO);
            }
            cursor = cursor.plusMonths(1);
        }
        return carry;
    }

    // -- Monthly summaries (log) --

    @Transactional(readOnly = true)
    public List<MonthlySummaryResponse> listSummaries(UUID userId) {
        return summaryRepo.findByUserIdOrderByPeriodDesc(userId).stream()
                .map(MonthlySummaryResponse::from)
                .toList();
    }

    @Transactional
    public MonthlySummaryResponse captureSnapshot(UUID userId, String period) {
        BudgetSummaryResponse live = summary(userId, period);

        MonthlySummary summary = summaryRepo.findByUserIdAndPeriod(userId, period)
                .orElse(MonthlySummary.builder()
                        .userId(userId)
                        .period(period)
                        .build());
        summary.setTotalIncome(live.totalIncome());
        summary.setTotalExpense(live.totalExpense());
        summary.setSavingsRate(live.savingsRate());

        summary = summaryRepo.save(summary);
        log.info("Monthly snapshot captured: period={} income={} expense={}", period, live.totalIncome(), live.totalExpense());
        return MonthlySummaryResponse.from(summary);
    }

    // -- Helpers --

    private TransactionResponse toResponse(BudgetTransaction t,
                                            Map<UUID, String[]> catLookup,
                                            Map<UUID, List<TransactionResponse.TagRef>> tagLookup) {
        String[] catInfo = catLookup.getOrDefault(t.getCategoryId(), new String[]{null, null});
        List<TransactionResponse.TagRef> tags = tagLookup.getOrDefault(t.getId(), List.of());
        return TransactionResponse.from(t, catInfo[0], catInfo[1], tags);
    }

    private Map<UUID, List<TransactionResponse.TagRef>> buildTagLookup(UUID userId, Collection<UUID> txnIds) {
        Map<UUID, List<TagService.TagSummary>> raw = tagService.loadTagsForTransactions(userId, txnIds);
        Map<UUID, List<TransactionResponse.TagRef>> out = new HashMap<>();
        raw.forEach((id, list) -> out.put(id, list.stream().map(TransactionResponse.TagRef::from).toList()));
        return out;
    }

    private Map<UUID, String[]> buildCategoryLookup(UUID userId) {
        Map<UUID, String[]> map = new HashMap<>();
        incomeRepo.findByUserIdOrderByNameAsc(userId)
                .forEach(c -> map.put(c.getId(), new String[]{c.getName(), c.getColor()}));
        expenseRepo.findByUserIdOrderByNameAsc(userId)
                .forEach(c -> map.put(c.getId(), new String[]{c.getName(), c.getColor()}));
        return map;
    }

    private static final UUID UNCATEGORIZED = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private List<BudgetSummaryResponse.CategoryAmount> groupByCategory(
            List<BudgetTransaction> txns,
            BudgetTransaction.TxnType type,
            Map<UUID, String[]> catLookup,
            BigDecimal total,
            Map<UUID, ExpenseCategory> expenseById,
            Map<UUID, BigDecimal> rolloverByCategory) {

        Map<UUID, BigDecimal> grouped = txns.stream()
                .filter(t -> t.getTxnType() == type)
                .collect(Collectors.groupingBy(
                        t -> t.getCategoryId() != null ? t.getCategoryId() : UNCATEGORIZED,
                        Collectors.reducing(BigDecimal.ZERO, BudgetTransaction::getAmount, BigDecimal::add)));

        // Ensure rollover-enabled categories surface even when spent == 0 this month.
        for (UUID catId : rolloverByCategory.keySet()) {
            grouped.putIfAbsent(catId, BigDecimal.ZERO);
        }

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    UUID catId = e.getKey();
                    String[] info = catLookup.getOrDefault(catId, new String[]{"Uncategorized", null});
                    BigDecimal pct = total.signum() > 0
                            ? e.getValue().divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;

                    ExpenseCategory cat = expenseById.get(catId);
                    BigDecimal base = cat != null ? cat.getBudgetAmount() : null;
                    BigDecimal rollover = rolloverByCategory.getOrDefault(catId, null);
                    BigDecimal effective = null;
                    if (base != null) {
                        effective = rollover != null ? base.add(rollover) : base;
                    }

                    return new BudgetSummaryResponse.CategoryAmount(
                            catId.equals(UNCATEGORIZED) ? null : catId,
                            info[0],
                            info[1],
                            e.getValue(),
                            pct,
                            base,
                            rollover,
                            effective
                    );
                })
                .toList();
    }
}
