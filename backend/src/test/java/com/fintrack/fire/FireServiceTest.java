package com.fintrack.fire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.budget.MonthlySummaryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.MonthlySummary;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.fire.dto.FireResponse;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FireServiceTest {

    @Mock MonthlySummaryRepository summaryRepo;
    @Mock TransactionRepository txnRepo;
    @Mock PortfolioRepository portfolioRepo;
    @Mock HoldingRepository holdingRepo;
    @Mock AssetRepository assetRepo;

    @InjectMocks FireService service;

    private final UUID userId = UUID.randomUUID();

    private MonthlySummary summary(String period, String income, String expense) {
        return MonthlySummary.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .period(period)
                .totalIncome(new BigDecimal(income))
                .totalExpense(new BigDecimal(expense))
                .build();
    }

    @Test
    void computeReturnsZeroesWhenNoDataAnywhere() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any())).thenReturn(null);
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        FireResponse res = service.compute(userId, null, null, null, null, null);

        assertThat(res.currentNetWorth()).isEqualByComparingTo("0");
        assertThat(res.avgMonthlyIncome()).isEqualByComparingTo("0");
        assertThat(res.avgMonthlyExpense()).isEqualByComparingTo("0");
        assertThat(res.targetNumber()).isEqualByComparingTo("0");
        assertThat(res.monthsToFi()).isNull();
        assertThat(res.sufficientData()).isFalse();
        assertThat(res.trajectory()).isNotEmpty();
    }

    @Test
    void computeUsesDefaultsWhenOverridesNull() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        FireResponse res = service.compute(userId, null, null, null, null, null);

        assertThat(res.withdrawalRate()).isEqualByComparingTo("0.04");
        assertThat(res.expectedReturn()).isEqualByComparingTo("0.07");
    }

    @Test
    void savingsRateComputedFromAverages() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "6000"),
                                summary("2026-02", "10000", "6000"),
                                summary("2026-01", "10000", "6000")));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        FireResponse res = service.compute(userId, null, null, null, null, null);

        assertThat(res.avgMonthlyIncome()).isEqualByComparingTo("10000.00");
        assertThat(res.avgMonthlyExpense()).isEqualByComparingTo("6000.00");
        assertThat(res.savingsRate()).isEqualByComparingTo("0.4000");
        assertThat(res.monthlyContribution()).isEqualByComparingTo("4000.00");
        assertThat(res.samplesUsed()).isEqualTo(3);
        assertThat(res.sufficientData()).isTrue();
    }

    @Test
    void targetNumberIs25xAnnualExpenseAtDefaultWithdrawal() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "20000", "10000"),
                                summary("2026-02", "20000", "10000"),
                                summary("2026-01", "20000", "10000")));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        FireResponse res = service.compute(userId, null, null, null, null, null);

        assertThat(res.targetNumber()).isEqualByComparingTo("3000000.00");
    }

    @Test
    void withdrawalOverrideChangesTargetAndZeroWithdrawalProducesZeroTarget() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        FireResponse res = service.compute(userId, new BigDecimal("0.05"), null, null, null, null);
        assertThat(res.targetNumber()).isEqualByComparingTo("1200000.00");

        FireResponse zero = service.compute(userId, BigDecimal.ZERO, null, null, null, null);
        assertThat(zero.targetNumber()).isEqualByComparingTo("0");
        assertThat(zero.monthsToFi()).isNull();
    }

    @Test
    void progressRatioCapsAtFour() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));

        FireResponse res =
                service.compute(userId, null, null, null, null, new BigDecimal("50000000"));

        assertThat(res.progressRatio()).isEqualByComparingTo("4");
        assertThat(res.monthsToFi()).isZero();
    }

    @Test
    void monthsToFiReachesZeroWhenAlreadyAtTarget() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));

        FireResponse res =
                service.compute(userId, null, null, null, null, new BigDecimal("1500001"));

        assertThat(res.monthsToFi()).isZero();
        assertThat(res.yearsToFi()).isEqualByComparingTo("0.00");
        assertThat(res.projectedFiDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void monthsToFiIsNullWhenContributionZeroAndReturnZero() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));

        FireResponse res =
                service.compute(
                        userId,
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        new BigDecimal("100"));

        assertThat(res.monthsToFi()).isNull();
        assertThat(res.projectedFiDate()).isNull();
    }

    @Test
    void monthsToFiIsPositiveForReachableTarget() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));

        FireResponse res =
                service.compute(
                        userId,
                        null,
                        null,
                        new BigDecimal("20000"),
                        null,
                        new BigDecimal("500000"));

        assertThat(res.monthsToFi()).isNotNull().isPositive();
        assertThat(res.yearsToFi()).isPositive();
        assertThat(res.projectedFiDate()).isAfter(LocalDate.now());
    }

    @Test
    void fallsBackToTransactionsWhenSummariesBelowMinSamples() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(List.of(summary("2026-03", "10000", "5000")));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.INCOME), any(), any()))
                .thenReturn(new BigDecimal("8000"));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(
                        eq(userId), eq(BudgetTransaction.TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("3000"));

        FireResponse res = service.compute(userId, null, null, null, null, null);

        assertThat(res.avgMonthlyIncome()).isEqualByComparingTo("8000.00");
        assertThat(res.avgMonthlyExpense()).isEqualByComparingTo("3000.00");
        assertThat(res.samplesUsed()).isEqualTo(12);
    }

    @Test
    void expenseOverrideReplacesAverageExpenseAndMonthlyContributionRespectsIt() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        FireResponse res = service.compute(userId, null, null, null, new BigDecimal("2000"), null);

        assertThat(res.avgMonthlyExpense()).isEqualByComparingTo("2000");
        assertThat(res.monthlyContribution()).isEqualByComparingTo("8000.00");
    }

    @Test
    void contributionOverrideBypassesIncomeMinusExpenseMath() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "9000"),
                                summary("2026-02", "10000", "9000"),
                                summary("2026-01", "10000", "9000")));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        FireResponse res = service.compute(userId, null, null, new BigDecimal("5000"), null, null);

        assertThat(res.monthlyContribution()).isEqualByComparingTo("5000");
    }

    @Test
    void netWorthSumsHoldingValuesWhenOverrideNull() {
        Portfolio p =
                Portfolio.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .name("P")
                        .active(true)
                        .build();
        Asset btc =
                Asset.builder()
                        .id(UUID.randomUUID())
                        .symbol("BTC")
                        .name("BTC")
                        .assetType(AssetType.CRYPTO)
                        .currency("TRY")
                        .price(new BigDecimal("100"))
                        .build();
        Asset eth =
                Asset.builder()
                        .id(UUID.randomUUID())
                        .symbol("ETH")
                        .name("ETH")
                        .assetType(AssetType.CRYPTO)
                        .currency("TRY")
                        .price(new BigDecimal("50"))
                        .build();
        PortfolioHolding hBtc =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(btc.getId())
                        .quantity(new BigDecimal("2"))
                        .build();
        PortfolioHolding hEth =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(eth.getId())
                        .quantity(new BigDecimal("4"))
                        .build();

        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p));
        when(holdingRepo.findByPortfolioId(p.getId())).thenReturn(List.of(hBtc, hEth));
        when(assetRepo.findAllById(any())).thenReturn(List.of(btc, eth));

        FireResponse res = service.compute(userId, null, null, null, null, null);

        assertThat(res.currentNetWorth()).isEqualByComparingTo("400.00");
    }

    @Test
    void netWorthSkipsHoldingsWithMissingAssetOrPrice() {
        Portfolio p =
                Portfolio.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .name("P")
                        .active(true)
                        .build();
        Asset btc =
                Asset.builder()
                        .id(UUID.randomUUID())
                        .symbol("BTC")
                        .name("BTC")
                        .assetType(AssetType.CRYPTO)
                        .currency("TRY")
                        .price(new BigDecimal("100"))
                        .build();
        Asset noPrice =
                Asset.builder()
                        .id(UUID.randomUUID())
                        .symbol("NP")
                        .name("NP")
                        .assetType(AssetType.CRYPTO)
                        .currency("TRY")
                        .price(null)
                        .build();
        PortfolioHolding good =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(btc.getId())
                        .quantity(new BigDecimal("1"))
                        .build();
        PortfolioHolding priceless =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(noPrice.getId())
                        .quantity(new BigDecimal("5"))
                        .build();
        PortfolioHolding orphan =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(UUID.randomUUID())
                        .quantity(new BigDecimal("7"))
                        .build();

        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId)).thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any())).thenReturn(null);
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p));
        when(holdingRepo.findByPortfolioId(p.getId())).thenReturn(List.of(good, priceless, orphan));
        when(assetRepo.findAllById(any())).thenReturn(List.of(btc, noPrice));

        FireResponse res = service.compute(userId, null, null, null, null, null);

        assertThat(res.currentNetWorth()).isEqualByComparingTo("100.00");
    }

    @Test
    void trajectoryAlwaysStartsAtDay0WithCurrentNetWorth() {
        when(summaryRepo.findByUserIdOrderByPeriodDesc(userId))
                .thenReturn(
                        List.of(
                                summary("2026-03", "10000", "5000"),
                                summary("2026-02", "10000", "5000"),
                                summary("2026-01", "10000", "5000")));

        FireResponse res = service.compute(userId, null, null, null, null, new BigDecimal("10000"));

        assertThat(res.trajectory()).isNotEmpty();
        assertThat(res.trajectory().get(0).year()).isZero();
        assertThat(res.trajectory().get(0).netWorth()).isEqualByComparingTo("10000.00");
    }
}
