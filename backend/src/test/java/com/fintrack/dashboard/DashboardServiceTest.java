package com.fintrack.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.bills.BillPaymentRepository;
import com.fintrack.bills.BillRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.BillPayment.PaymentStatus;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.Portfolio.PortfolioType;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.dashboard.dto.DashboardResponse;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock PortfolioRepository portfolioRepo;
    @Mock HoldingRepository holdingRepo;
    @Mock AssetRepository assetRepo;
    @Mock TransactionRepository txnRepo;
    @Mock BillRepository billRepo;
    @Mock BillPaymentRepository billPaymentRepo;

    @InjectMocks DashboardService service;

    private final UUID userId = UUID.randomUUID();

    private Portfolio portfolio(String name, PortfolioType type) {
        return Portfolio.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .portfolioType(type)
                .active(true)
                .build();
    }

    private Asset asset(String symbol, String price) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol)
                .assetType(AssetType.CRYPTO)
                .currency("TRY")
                .price(new BigDecimal(price))
                .build();
    }

    private PortfolioHolding holding(UUID portfolioId, UUID assetId, String qty, String cost) {
        return PortfolioHolding.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .assetId(assetId)
                .quantity(new BigDecimal(qty))
                .avgCostTry(new BigDecimal(cost))
                .build();
    }

    private Bill bill(String name, String amount, int dueDay) {
        return Bill.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .amount(new BigDecimal(amount))
                .dueDay(dueDay)
                .active(true)
                .build();
    }

    @Test
    void buildsEmptyDashboardWhenNoData() {
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        DashboardResponse res = service.build(userId);

        assertThat(res.totalNetWorth()).isEqualByComparingTo("0");
        assertThat(res.portfolios()).isEmpty();
        assertThat(res.upcomingBills()).isEmpty();
        assertThat(res.budget().period()).isEqualTo(YearMonth.now().toString());
        assertThat(res.budget().savingsRate()).isEqualByComparingTo("0");
    }

    @Test
    void aggregatesPortfolioValueAndCostIntoPnL() {
        Portfolio p = portfolio("Main", PortfolioType.INDIVIDUAL);
        Asset btc = asset("BTC", "100");
        Asset eth = asset("ETH", "50");
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p));
        when(holdingRepo.findByPortfolioId(p.getId()))
                .thenReturn(
                        List.of(
                                holding(p.getId(), btc.getId(), "2", "80"),
                                holding(p.getId(), eth.getId(), "3", "40")));
        when(assetRepo.findAllById(any())).thenReturn(List.of(btc, eth));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        DashboardResponse res = service.build(userId);

        DashboardResponse.PortfolioSummary s = res.portfolios().get(0);
        assertThat(s.valueTry()).isEqualByComparingTo("350");
        assertThat(s.costTry()).isEqualByComparingTo("280");
        assertThat(s.pnlTry()).isEqualByComparingTo("70");
        assertThat(s.pnlPercent()).isEqualByComparingTo("0.250000");
        assertThat(res.totalNetWorth()).isEqualByComparingTo("350");
    }

    @Test
    void pnlPercentNullWhenCostIsZero() {
        Portfolio p = portfolio("New", PortfolioType.CRYPTO);
        Asset btc = asset("BTC", "100");
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p));
        when(holdingRepo.findByPortfolioId(p.getId()))
                .thenReturn(List.of(holding(p.getId(), btc.getId(), "1", "0")));
        when(assetRepo.findAllById(any())).thenReturn(List.of(btc));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        DashboardResponse res = service.build(userId);

        assertThat(res.portfolios().get(0).pnlPercent()).isNull();
    }

    @Test
    void skipsHoldingsWithMissingAssetOrNullPrice() {
        Portfolio p = portfolio("Main", PortfolioType.INDIVIDUAL);
        Asset btc = asset("BTC", "100");
        Asset priceless =
                Asset.builder()
                        .id(UUID.randomUUID())
                        .symbol("NP")
                        .name("NP")
                        .assetType(AssetType.CRYPTO)
                        .currency("TRY")
                        .price(null)
                        .build();
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(p));
        when(holdingRepo.findByPortfolioId(p.getId()))
                .thenReturn(
                        List.of(
                                holding(p.getId(), btc.getId(), "1", "90"),
                                holding(p.getId(), priceless.getId(), "5", "10"),
                                holding(p.getId(), UUID.randomUUID(), "7", "3")));
        when(assetRepo.findAllById(any())).thenReturn(List.of(btc, priceless));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        DashboardResponse res = service.build(userId);

        DashboardResponse.PortfolioSummary s = res.portfolios().get(0);
        assertThat(s.valueTry()).isEqualByComparingTo("100");
        assertThat(s.costTry()).isEqualByComparingTo("140");
    }

    @Test
    void budgetComputesSavingsRatePercent() {
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(new BigDecimal("10000"));
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("7500"));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        DashboardResponse res = service.build(userId);

        assertThat(res.budget().income()).isEqualByComparingTo("10000");
        assertThat(res.budget().expense()).isEqualByComparingTo("7500");
        assertThat(res.budget().net()).isEqualByComparingTo("2500");
        assertThat(res.budget().savingsRate()).isEqualByComparingTo("25.0000");
    }

    @Test
    void budgetSavingsRateZeroWhenIncomeZero() {
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.INCOME), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(txnRepo.sumByUserIdAndTypeAndDateRange(eq(userId), eq(TxnType.EXPENSE), any(), any()))
                .thenReturn(new BigDecimal("500"));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of());

        DashboardResponse res = service.build(userId);

        assertThat(res.budget().savingsRate()).isEqualByComparingTo("0");
        assertThat(res.budget().net()).isEqualByComparingTo("-500");
    }

    @Test
    void upcomingBillsExcludePaidBills() {
        Bill paid = bill("Rent", "1000", 15);
        Bill pending = bill("Internet", "200", 20);
        String period = YearMonth.now().toString();
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId))
                .thenReturn(List.of(paid, pending));
        BillPayment paidEntry =
                BillPayment.builder()
                        .id(UUID.randomUUID())
                        .billId(paid.getId())
                        .period(period)
                        .amount(new BigDecimal("1000"))
                        .status(PaymentStatus.PAID)
                        .build();
        when(billPaymentRepo.findByBillIdAndPeriod(paid.getId(), period))
                .thenReturn(Optional.of(paidEntry));
        when(billPaymentRepo.findByBillIdAndPeriod(pending.getId(), period))
                .thenReturn(Optional.empty());

        DashboardResponse res = service.build(userId);

        assertThat(res.upcomingBills()).hasSize(1);
        assertThat(res.upcomingBills().get(0).name()).isEqualTo("Internet");
        assertThat(res.upcomingBills().get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void upcomingBillsSortByDaysUntilDueAndCapAtFive() {
        List<Bill> six =
                List.of(
                        bill("A", "100", 28),
                        bill("B", "100", 2),
                        bill("C", "100", 10),
                        bill("D", "100", 15),
                        bill("E", "100", 20),
                        bill("F", "100", 25));
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(six);
        when(billPaymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());

        DashboardResponse res = service.build(userId);

        assertThat(res.upcomingBills()).hasSize(5);
        LocalDate today = LocalDate.now();
        for (int i = 1; i < res.upcomingBills().size(); i++) {
            assertThat(res.upcomingBills().get(i).daysUntilDue())
                    .isGreaterThanOrEqualTo(res.upcomingBills().get(i - 1).daysUntilDue());
        }
        // sanity: all daysUntilDue are non-negative
        assertThat(res.upcomingBills())
                .allSatisfy(b -> assertThat(b.daysUntilDue()).isGreaterThanOrEqualTo(0L));
        assertThat(today).isNotNull();
    }

    @Test
    void upcomingBillsAdvanceToNextMonthWhenDueDayAlreadyPassed() {
        int todayDay = LocalDate.now().getDayOfMonth();
        if (todayDay <= 1) {
            return; // skip when on the first day; no earlier day to test
        }
        Bill past = bill("Past", "100", todayDay - 1);
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of(past));
        when(billPaymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());

        DashboardResponse res = service.build(userId);

        assertThat(res.upcomingBills()).hasSize(1);
        assertThat(res.upcomingBills().get(0).daysUntilDue()).isPositive();
    }

    @Test
    void skippedBillStatusPassesThroughButStillIncluded() {
        Bill b = bill("Rent", "1000", 15);
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());
        when(txnRepo.sumByUserIdAndTypeAndDateRange(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of(b));
        BillPayment skipped =
                BillPayment.builder()
                        .id(UUID.randomUUID())
                        .billId(b.getId())
                        .period(YearMonth.now().toString())
                        .amount(new BigDecimal("1000"))
                        .status(PaymentStatus.SKIPPED)
                        .build();
        when(billPaymentRepo.findByBillIdAndPeriod(b.getId(), YearMonth.now().toString()))
                .thenReturn(Optional.of(skipped));

        DashboardResponse res = service.build(userId);

        assertThat(res.upcomingBills()).hasSize(1);
        assertThat(res.upcomingBills().get(0).status()).isEqualTo("SKIPPED");
    }
}
