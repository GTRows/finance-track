package com.fintrack.report.capitalgains;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.dividend.DividendRepository;
import com.fintrack.portfolio.transaction.InvestmentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapitalGainsServiceTest {

    @Mock PortfolioRepository portfolioRepo;
    @Mock InvestmentTransactionRepository txnRepo;
    @Mock AssetRepository assetRepo;
    @Mock DividendRepository dividendRepo;

    @InjectMocks CapitalGainsService service;

    private UUID userId;
    private UUID portfolioId;
    private UUID assetId;
    private Portfolio portfolio;
    private Asset asset;
    private long sequence;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        portfolioId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        portfolio = Portfolio.builder().id(portfolioId).userId(userId).name("Main").build();
        asset = Asset.builder().id(assetId).symbol("BTC").name("Bitcoin").build();
        sequence = 0L;

        lenient().when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(portfolio));
        lenient().when(assetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        lenient().when(dividendRepo.sumNetByPortfoliosAndRange(anyList(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void computesRealizedGainForSimpleBuyThenSell() {
        // Buy 10 @ 100 (fee 5) -> cost basis 1005
        // Sell 10 @ 150 (fee 7) -> gross 1500, net 1493
        // Gain = 1493 - 1005 = 488
        txnRepoReturns(
                buy(LocalDate.of(2026, 1, 1), "10", "100", "5"),
                sell(LocalDate.of(2026, 6, 1), "10", "150", "7")
        );

        CapitalGainsResponse report = service.compute(userId, null);

        assertThat(report.totalProceeds()).isEqualByComparingTo("1500");
        assertThat(report.totalCostBasis()).isEqualByComparingTo("1005");
        assertThat(report.totalFees()).isEqualByComparingTo("7");
        assertThat(report.realizedGain()).isEqualByComparingTo("488");
        assertThat(report.events()).hasSize(1);
        assertThat(report.events().get(0).realizedGain()).isEqualByComparingTo("488");
    }

    @Test
    void usesWeightedAverageCostAcrossMultipleBuys() {
        // Buy 10 @ 100 (fee 0) -> lot qty 10, cost 1000, avg 100
        // Buy 10 @ 200 (fee 0) -> lot qty 20, cost 3000, avg 150
        // Sell 10 @ 250 (fee 0) -> cost basis = 10 * 150 = 1500, proceeds 2500, gain 1000
        txnRepoReturns(
                buy(LocalDate.of(2026, 1, 1), "10", "100", "0"),
                buy(LocalDate.of(2026, 2, 1), "10", "200", "0"),
                sell(LocalDate.of(2026, 3, 1), "10", "250", "0")
        );

        CapitalGainsResponse report = service.compute(userId, null);

        assertThat(report.realizedGain()).isEqualByComparingTo("1000");
        assertThat(report.totalCostBasis()).isEqualByComparingTo("1500");
        assertThat(report.totalProceeds()).isEqualByComparingTo("2500");
    }

    @Test
    void handlesPartialSellCorrectly() {
        // Buy 10 @ 100 -> lot qty 10, cost 1000
        // Sell 4 @ 150 -> cost basis = 4 * 100 = 400, proceeds 600, gain 200
        // Remaining lot: qty 6, cost 600
        txnRepoReturns(
                buy(LocalDate.of(2026, 1, 1), "10", "100", "0"),
                sell(LocalDate.of(2026, 2, 1), "4", "150", "0")
        );

        CapitalGainsResponse report = service.compute(userId, null);

        assertThat(report.realizedGain()).isEqualByComparingTo("200");
        assertThat(report.events()).hasSize(1);
        assertThat(report.events().get(0).quantity()).isEqualByComparingTo("4");
        assertThat(report.events().get(0).costBasis()).isEqualByComparingTo("400");
    }

    @Test
    void aggregatesGainsByYear() {
        txnRepoReturns(
                buy(LocalDate.of(2024, 1, 1), "10", "100", "0"),
                sell(LocalDate.of(2025, 3, 1), "4", "150", "0"),
                sell(LocalDate.of(2026, 4, 1), "6", "200", "0")
        );

        CapitalGainsResponse report = service.compute(userId, null);

        assertThat(report.byYear()).hasSize(2);
        assertThat(report.byYear().get(0).year()).isEqualTo(2025);
        assertThat(report.byYear().get(0).realizedGain()).isEqualByComparingTo("200");
        assertThat(report.byYear().get(1).year()).isEqualTo(2026);
        assertThat(report.byYear().get(1).realizedGain()).isEqualByComparingTo("600");
    }

    @Test
    void yearFilterOnlyKeepsEventsInThatYear() {
        txnRepoReturns(
                buy(LocalDate.of(2024, 1, 1), "10", "100", "0"),
                sell(LocalDate.of(2025, 3, 1), "4", "150", "0"),
                sell(LocalDate.of(2026, 4, 1), "6", "200", "0")
        );

        CapitalGainsResponse report = service.compute(userId, 2026);

        assertThat(report.events()).hasSize(1);
        assertThat(report.events().get(0).txnDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(report.realizedGain()).isEqualByComparingTo("600");
    }

    @Test
    void sellWithoutPriorBuyIsIgnored() {
        txnRepoReturns(
                sell(LocalDate.of(2026, 1, 1), "5", "100", "0")
        );

        CapitalGainsResponse report = service.compute(userId, null);

        assertThat(report.events()).isEmpty();
        assertThat(report.realizedGain()).isEqualByComparingTo("0");
    }

    @Test
    void oversellCapsAtAvailableQuantity() {
        // Buy 10 @ 100, then attempt to sell 20 @ 150
        // Only 10 are available; cost basis 1000, proceeds 1500 (on 10 sold qty pricing is from txn),
        // Actually proceeds scales to what was sold: qty=20 in txn but we cap at 10.
        // Txn amountTry = 20*150 = 3000 (net of fee). We cap qty at 10 but use full amountTry.
        // This is a safety path; the business rule prevents it in practice via applyToHolding.
        txnRepoReturns(
                buy(LocalDate.of(2026, 1, 1), "10", "100", "0"),
                sell(LocalDate.of(2026, 2, 1), "20", "150", "0")
        );

        CapitalGainsResponse report = service.compute(userId, null);

        assertThat(report.events()).hasSize(1);
        assertThat(report.events().get(0).quantity()).isEqualByComparingTo("10");
        assertThat(report.events().get(0).costBasis()).isEqualByComparingTo("1000");
    }

    @Test
    void includesDividendNetInYearSummary() {
        txnRepoReturns(
                buy(LocalDate.of(2026, 1, 1), "10", "100", "0"),
                sell(LocalDate.of(2026, 6, 1), "10", "150", "0")
        );
        when(dividendRepo.sumNetByPortfoliosAndRange(
                eq(List.of(portfolioId)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 12, 31))
        )).thenReturn(new BigDecimal("125"));

        CapitalGainsResponse report = service.compute(userId, 2026);

        assertThat(report.dividendsNetTry()).isEqualByComparingTo("125");
        assertThat(report.byYear().get(0).dividendsNetTry()).isEqualByComparingTo("125");
    }

    @Test
    void returnsEmptyWhenUserHasNoPortfolios() {
        when(portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        CapitalGainsResponse report = service.compute(userId, null);

        assertThat(report.events()).isEmpty();
        assertThat(report.byYear()).isEmpty();
        assertThat(report.realizedGain()).isEqualByComparingTo("0");
    }

    @Test
    void besContributionIncreasesCostBasisLikeBuy() {
        // BES contribution: qty 5 at cost 100 (amount stored as 500)
        // Sell 5 at 200 (amount stored as 1000 net)
        // Cost basis 500, proceeds 1000, gain 500
        txnRepoReturns(
                bes(LocalDate.of(2026, 1, 1), "5", "100"),
                sell(LocalDate.of(2026, 6, 1), "5", "200", "0")
        );

        CapitalGainsResponse report = service.compute(userId, null);

        assertThat(report.realizedGain()).isEqualByComparingTo("500");
    }

    private void txnRepoReturns(InvestmentTransaction... txns) {
        when(txnRepo.findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(portfolioId))
                .thenReturn(List.of(txns));
    }

    private InvestmentTransaction buy(LocalDate date, String qty, String price, String fee) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal p = new BigDecimal(price);
        BigDecimal f = new BigDecimal(fee);
        BigDecimal amount = p.multiply(q).add(f);
        return InvestmentTransaction.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .assetId(assetId)
                .txnType(TxnType.BUY)
                .quantity(q)
                .priceTry(p)
                .amountTry(amount.setScale(4, RoundingMode.HALF_UP))
                .feeTry(f)
                .txnDate(date)
                .createdAt(Instant.ofEpochSecond(1_700_000_000L + (++sequence)))
                .build();
    }

    private InvestmentTransaction sell(LocalDate date, String qty, String price, String fee) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal p = new BigDecimal(price);
        BigDecimal f = new BigDecimal(fee);
        BigDecimal amount = p.multiply(q).subtract(f);
        return InvestmentTransaction.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .assetId(assetId)
                .txnType(TxnType.SELL)
                .quantity(q)
                .priceTry(p)
                .amountTry(amount.setScale(4, RoundingMode.HALF_UP))
                .feeTry(f)
                .txnDate(date)
                .createdAt(Instant.ofEpochSecond(1_700_000_000L + (++sequence)))
                .build();
    }

    private InvestmentTransaction bes(LocalDate date, String qty, String price) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal p = new BigDecimal(price);
        BigDecimal amount = p.multiply(q);
        return InvestmentTransaction.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .assetId(assetId)
                .txnType(TxnType.BES_CONTRIBUTION)
                .quantity(q)
                .priceTry(p)
                .amountTry(amount.setScale(4, RoundingMode.HALF_UP))
                .feeTry(BigDecimal.ZERO)
                .txnDate(date)
                .createdAt(Instant.ofEpochSecond(1_700_000_000L + (++sequence)))
                .build();
    }
}
