package com.fintrack.fire;

import com.fintrack.asset.AssetRepository;
import com.fintrack.budget.MonthlySummaryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.*;
import com.fintrack.fire.dto.FireResponse;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FireService {

    private static final MathContext MC = new MathContext(14, RoundingMode.HALF_UP);
    private static final int SAMPLE_MONTHS = 12;
    private static final int MIN_SAMPLES = 3;
    private static final int MAX_PROJECTION_YEARS = 60;

    private static final BigDecimal DEFAULT_WITHDRAWAL = new BigDecimal("0.04");
    private static final BigDecimal DEFAULT_RETURN = new BigDecimal("0.07");

    private final MonthlySummaryRepository summaryRepo;
    private final TransactionRepository txnRepo;
    private final PortfolioRepository portfolioRepo;
    private final HoldingRepository holdingRepo;
    private final AssetRepository assetRepo;

    @Transactional(readOnly = true)
    public FireResponse compute(
            UUID userId,
            BigDecimal withdrawalOverride,
            BigDecimal returnOverride,
            BigDecimal monthlyContributionOverride,
            BigDecimal monthlyExpenseOverride,
            BigDecimal netWorthOverride) {

        BigDecimal withdrawal =
                withdrawalOverride != null ? withdrawalOverride : DEFAULT_WITHDRAWAL;
        BigDecimal expectedReturn = returnOverride != null ? returnOverride : DEFAULT_RETURN;

        BigDecimal netWorth = netWorthOverride != null ? netWorthOverride : sumNetWorth(userId);

        BudgetAverages avg = averagesFromSummaries(userId);
        if (avg == null) avg = averagesFromTransactions(userId);

        BigDecimal avgIncome = avg.income;
        BigDecimal avgExpense =
                monthlyExpenseOverride != null ? monthlyExpenseOverride : avg.expense;
        BigDecimal monthlyContribution =
                monthlyContributionOverride != null
                        ? monthlyContributionOverride
                        : avgIncome.subtract(avgExpense).max(BigDecimal.ZERO);

        BigDecimal savingsRate =
                avgIncome.signum() > 0
                        ? avgIncome.subtract(avgExpense).divide(avgIncome, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        BigDecimal annualExpense = avgExpense.multiply(BigDecimal.valueOf(12));
        BigDecimal target =
                withdrawal.signum() > 0
                        ? annualExpense.divide(withdrawal, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        BigDecimal progressRatio =
                target.signum() > 0
                        ? netWorth.divide(target, 4, RoundingMode.HALF_UP)
                                .min(BigDecimal.valueOf(4))
                        : BigDecimal.ZERO;

        int months = monthsToTarget(netWorth, target, monthlyContribution, expectedReturn);
        LocalDate projectedDate = months >= 0 ? LocalDate.now().plusMonths(months) : null;
        BigDecimal yearsToFi =
                months >= 0
                        ? BigDecimal.valueOf(months)
                                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                        : null;

        List<FireResponse.TrajectoryPoint> trajectory =
                buildTrajectory(netWorth, monthlyContribution, expectedReturn, months);

        boolean sufficient = avg.samples >= MIN_SAMPLES;

        return new FireResponse(
                round(netWorth),
                round(avgIncome),
                round(avgExpense),
                savingsRate,
                round(monthlyContribution),
                withdrawal,
                expectedReturn,
                round(target),
                progressRatio,
                months >= 0 ? months : null,
                yearsToFi,
                projectedDate,
                avg.samples,
                sufficient,
                trajectory);
    }

    private BigDecimal sumNetWorth(UUID userId) {
        List<Portfolio> portfolios =
                portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);
        if (portfolios.isEmpty()) return BigDecimal.ZERO;

        Set<UUID> assetIds = new HashSet<>();
        Map<UUID, List<PortfolioHolding>> byPortfolio = new HashMap<>();
        for (Portfolio p : portfolios) {
            List<PortfolioHolding> holdings = holdingRepo.findByPortfolioId(p.getId());
            byPortfolio.put(p.getId(), holdings);
            holdings.forEach(h -> assetIds.add(h.getAssetId()));
        }

        Map<UUID, Asset> assets = new HashMap<>();
        if (!assetIds.isEmpty()) {
            assetRepo.findAllById(assetIds).forEach(a -> assets.put(a.getId(), a));
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Portfolio p : portfolios) {
            for (PortfolioHolding h : byPortfolio.getOrDefault(p.getId(), List.of())) {
                Asset asset = assets.get(h.getAssetId());
                if (asset == null || asset.getPrice() == null) continue;
                BigDecimal qty = h.getQuantity() != null ? h.getQuantity() : BigDecimal.ZERO;
                total = total.add(asset.getPrice().multiply(qty));
            }
        }
        return total;
    }

    private record BudgetAverages(BigDecimal income, BigDecimal expense, int samples) {}

    private BudgetAverages averagesFromSummaries(UUID userId) {
        List<MonthlySummary> summaries = summaryRepo.findByUserIdOrderByPeriodDesc(userId);
        if (summaries.size() < MIN_SAMPLES) return null;

        List<MonthlySummary> window =
                summaries.subList(0, Math.min(SAMPLE_MONTHS, summaries.size()));
        BigDecimal incomeSum = BigDecimal.ZERO;
        BigDecimal expenseSum = BigDecimal.ZERO;
        for (MonthlySummary s : window) {
            incomeSum = incomeSum.add(nvl(s.getTotalIncome()));
            expenseSum = expenseSum.add(nvl(s.getTotalExpense()));
        }
        int n = window.size();
        return new BudgetAverages(
                incomeSum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP),
                expenseSum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP),
                n);
    }

    private BudgetAverages averagesFromTransactions(UUID userId) {
        YearMonth current = YearMonth.now();
        BigDecimal incomeSum = BigDecimal.ZERO;
        BigDecimal expenseSum = BigDecimal.ZERO;
        int samples = 0;

        for (int i = 0; i < SAMPLE_MONTHS; i++) {
            YearMonth ym = current.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            BigDecimal inc =
                    nvl(
                            txnRepo.sumByUserIdAndTypeAndDateRange(
                                    userId, BudgetTransaction.TxnType.INCOME, from, to));
            BigDecimal exp =
                    nvl(
                            txnRepo.sumByUserIdAndTypeAndDateRange(
                                    userId, BudgetTransaction.TxnType.EXPENSE, from, to));
            if (inc.signum() == 0 && exp.signum() == 0) continue;
            incomeSum = incomeSum.add(inc);
            expenseSum = expenseSum.add(exp);
            samples++;
        }

        if (samples == 0) return new BudgetAverages(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        return new BudgetAverages(
                incomeSum.divide(BigDecimal.valueOf(samples), 2, RoundingMode.HALF_UP),
                expenseSum.divide(BigDecimal.valueOf(samples), 2, RoundingMode.HALF_UP),
                samples);
    }

    private int monthsToTarget(
            BigDecimal start, BigDecimal target, BigDecimal contribution, BigDecimal annualReturn) {
        if (target.signum() <= 0) return -1;
        if (start.compareTo(target) >= 0) return 0;
        if (contribution.signum() <= 0 && annualReturn.signum() <= 0) return -1;

        BigDecimal monthlyReturn = annualReturn.divide(BigDecimal.valueOf(12), MC);
        BigDecimal balance = start;
        int maxMonths = MAX_PROJECTION_YEARS * 12;

        for (int m = 1; m <= maxMonths; m++) {
            balance = balance.multiply(BigDecimal.ONE.add(monthlyReturn), MC).add(contribution);
            if (balance.compareTo(target) >= 0) return m;
        }
        return -1;
    }

    private List<FireResponse.TrajectoryPoint> buildTrajectory(
            BigDecimal start, BigDecimal contribution, BigDecimal annualReturn, int monthsToFi) {
        BigDecimal monthlyReturn = annualReturn.divide(BigDecimal.valueOf(12), MC);
        int years = monthsToFi > 0 ? Math.min(MAX_PROJECTION_YEARS, (monthsToFi / 12) + 2) : 20;

        List<FireResponse.TrajectoryPoint> points = new ArrayList<>();
        BigDecimal balance = start;
        LocalDate today = LocalDate.now();
        points.add(new FireResponse.TrajectoryPoint(0, today, round(balance)));

        for (int year = 1; year <= years; year++) {
            for (int m = 0; m < 12; m++) {
                balance = balance.multiply(BigDecimal.ONE.add(monthlyReturn), MC).add(contribution);
            }
            points.add(
                    new FireResponse.TrajectoryPoint(year, today.plusYears(year), round(balance)));
        }
        return points;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
