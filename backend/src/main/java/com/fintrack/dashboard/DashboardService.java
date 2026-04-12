package com.fintrack.dashboard;

import com.fintrack.asset.AssetRepository;
import com.fintrack.bills.BillPaymentRepository;
import com.fintrack.bills.BillRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.*;
import com.fintrack.dashboard.dto.DashboardResponse;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PortfolioRepository portfolioRepo;
    private final HoldingRepository holdingRepo;
    private final AssetRepository assetRepo;
    private final TransactionRepository txnRepo;
    private final BillRepository billRepo;
    private final BillPaymentRepository billPaymentRepo;

    @Transactional(readOnly = true)
    public DashboardResponse build(UUID userId) {
        List<DashboardResponse.PortfolioSummary> portfolios = buildPortfolios(userId);
        BigDecimal netWorth = portfolios.stream()
                .map(DashboardResponse.PortfolioSummary::valueTry)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DashboardResponse.BudgetOverview budget = buildBudget(userId);
        List<DashboardResponse.UpcomingBill> bills = buildUpcomingBills(userId);

        return new DashboardResponse(netWorth, portfolios, budget, bills);
    }

    private List<DashboardResponse.PortfolioSummary> buildPortfolios(UUID userId) {
        List<Portfolio> portfolios = portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);
        if (portfolios.isEmpty()) return List.of();

        Set<UUID> allAssetIds = new HashSet<>();
        Map<UUID, List<PortfolioHolding>> holdingsByPortfolio = new HashMap<>();
        for (Portfolio p : portfolios) {
            List<PortfolioHolding> holdings = holdingRepo.findByPortfolioId(p.getId());
            holdingsByPortfolio.put(p.getId(), holdings);
            holdings.forEach(h -> allAssetIds.add(h.getAssetId()));
        }

        Map<UUID, Asset> assetsById = new HashMap<>();
        if (!allAssetIds.isEmpty()) {
            assetRepo.findAllById(allAssetIds).forEach(a -> assetsById.put(a.getId(), a));
        }

        List<DashboardResponse.PortfolioSummary> result = new ArrayList<>();
        for (Portfolio p : portfolios) {
            BigDecimal totalValue = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            for (PortfolioHolding h : holdingsByPortfolio.getOrDefault(p.getId(), List.of())) {
                Asset asset = assetsById.get(h.getAssetId());
                if (asset == null) continue;
                BigDecimal qty = h.getQuantity() != null ? h.getQuantity() : BigDecimal.ZERO;
                if (asset.getPrice() != null) totalValue = totalValue.add(asset.getPrice().multiply(qty));
                if (h.getAvgCostTry() != null) totalCost = totalCost.add(h.getAvgCostTry().multiply(qty));
            }
            BigDecimal pnl = totalValue.subtract(totalCost);
            BigDecimal pnlPct = totalCost.signum() > 0
                    ? pnl.divide(totalCost, 6, RoundingMode.HALF_UP)
                    : null;

            result.add(new DashboardResponse.PortfolioSummary(
                    p.getId(), p.getName(), p.getPortfolioType().name(),
                    totalValue, totalCost, pnl, pnlPct));
        }
        return result;
    }

    private DashboardResponse.BudgetOverview buildBudget(UUID userId) {
        YearMonth ym = YearMonth.now();
        String period = ym.toString();
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        BigDecimal income = txnRepo.sumByUserIdAndTypeAndDateRange(userId, BudgetTransaction.TxnType.INCOME, from, to);
        BigDecimal expense = txnRepo.sumByUserIdAndTypeAndDateRange(userId, BudgetTransaction.TxnType.EXPENSE, from, to);
        BigDecimal net = income.subtract(expense);
        BigDecimal savingsRate = income.signum() > 0
                ? net.divide(income, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new DashboardResponse.BudgetOverview(period, income, expense, net, savingsRate);
    }

    private List<DashboardResponse.UpcomingBill> buildUpcomingBills(UUID userId) {
        LocalDate today = LocalDate.now();
        String currentPeriod = today.getYear() + "-" + String.format("%02d", today.getMonthValue());

        return billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId).stream()
                .map(bill -> {
                    int dueDay = Math.min(bill.getDueDay(), today.lengthOfMonth());
                    LocalDate dueDate = today.withDayOfMonth(dueDay);
                    if (dueDate.isBefore(today)) {
                        dueDate = dueDate.plusMonths(1);
                        dueDate = dueDate.withDayOfMonth(Math.min(bill.getDueDay(), dueDate.lengthOfMonth()));
                    }
                    long daysUntil = ChronoUnit.DAYS.between(today, dueDate);

                    String status = billPaymentRepo.findByBillIdAndPeriod(bill.getId(), currentPeriod)
                            .map(p -> p.getStatus().name())
                            .orElse("PENDING");

                    return new DashboardResponse.UpcomingBill(
                            bill.getId(), bill.getName(), bill.getAmount(),
                            bill.getDueDay(), daysUntil, status);
                })
                .filter(b -> !"PAID".equals(b.status()))
                .sorted(Comparator.comparingLong(DashboardResponse.UpcomingBill::daysUntilDue))
                .limit(5)
                .toList();
    }
}
