package com.fintrack.budget;

import com.fintrack.budget.dto.*;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.entity.MonthlySummary;
import com.fintrack.common.exception.ResourceNotFoundException;
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
import java.util.function.Function;
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

    // -- Transactions --

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(UUID userId, String month,
                                                       BudgetTransaction.TxnType type,
                                                       Pageable pageable) {
        YearMonth ym = YearMonth.parse(month, PERIOD_FMT);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Page<BudgetTransaction> page;
        if (type != null) {
            page = txnRepo.findByUserIdAndTxnTypeAndTxnDateBetweenOrderByTxnDateDesc(userId, type, from, to, pageable);
        } else {
            page = txnRepo.findByUserIdAndTxnDateBetweenOrderByTxnDateDesc(userId, from, to, pageable);
        }

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        return page.map(t -> {
            String[] catInfo = catLookup.getOrDefault(t.getCategoryId(), new String[]{null, null});
            return TransactionResponse.from(t, catInfo[0], catInfo[1]);
        });
    }

    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest req) {
        BudgetTransaction txn = BudgetTransaction.builder()
                .userId(userId)
                .txnType(req.txnType())
                .amount(req.amount())
                .categoryId(req.categoryId())
                .description(req.description())
                .txnDate(req.txnDate())
                .recurring(req.isRecurring())
                .tags(req.tags())
                .build();
        txn = txnRepo.save(txn);
        log.info("Transaction created: id={} type={} amount={}", txn.getId(), txn.getTxnType(), txn.getAmount());

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        String[] catInfo = catLookup.getOrDefault(txn.getCategoryId(), new String[]{null, null});
        return TransactionResponse.from(txn, catInfo[0], catInfo[1]);
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
        txn.setTags(req.tags());

        Map<UUID, String[]> catLookup = buildCategoryLookup(userId);
        String[] catInfo = catLookup.getOrDefault(txn.getCategoryId(), new String[]{null, null});
        return TransactionResponse.from(txn, catInfo[0], catInfo[1]);
    }

    @Transactional
    public void delete(UUID userId, UUID txnId) {
        BudgetTransaction txn = txnRepo.findByIdAndUserId(txnId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        txnRepo.delete(txn);
        log.info("Transaction deleted: id={}", txnId);
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

        List<BudgetSummaryResponse.CategoryAmount> incByCat = groupByCategory(
                all.getContent(), BudgetTransaction.TxnType.INCOME, catLookup, totalIncome);
        List<BudgetSummaryResponse.CategoryAmount> expByCat = groupByCategory(
                all.getContent(), BudgetTransaction.TxnType.EXPENSE, catLookup, totalExpense);

        return new BudgetSummaryResponse(month, totalIncome, totalExpense, net, savingsRate, incByCat, expByCat);
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

    private Map<UUID, String[]> buildCategoryLookup(UUID userId) {
        Map<UUID, String[]> map = new HashMap<>();
        incomeRepo.findByUserIdOrderByNameAsc(userId)
                .forEach(c -> map.put(c.getId(), new String[]{c.getName(), c.getColor()}));
        expenseRepo.findByUserIdOrderByNameAsc(userId)
                .forEach(c -> map.put(c.getId(), new String[]{c.getName(), c.getColor()}));
        return map;
    }

    private List<BudgetSummaryResponse.CategoryAmount> groupByCategory(
            List<BudgetTransaction> txns,
            BudgetTransaction.TxnType type,
            Map<UUID, String[]> catLookup,
            BigDecimal total) {

        Map<UUID, BigDecimal> grouped = txns.stream()
                .filter(t -> t.getTxnType() == type)
                .collect(Collectors.groupingBy(
                        t -> t.getCategoryId() != null ? t.getCategoryId() : UUID.fromString("00000000-0000-0000-0000-000000000000"),
                        Collectors.reducing(BigDecimal.ZERO, BudgetTransaction::getAmount, BigDecimal::add)));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    String[] info = catLookup.getOrDefault(e.getKey(), new String[]{"Uncategorized", null});
                    BigDecimal pct = total.signum() > 0
                            ? e.getValue().divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;
                    return new BudgetSummaryResponse.CategoryAmount(info[0], info[1], e.getValue(), pct);
                })
                .toList();
    }
}
