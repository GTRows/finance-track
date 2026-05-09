package com.fintrack.analytics.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fintrack.analytics.compare.dto.PortfolioComparisonResponse;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import com.fintrack.portfolio.transaction.InvestmentTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioComparisonServiceTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock SnapshotRepository snapshotRepository;
    @Mock InvestmentTransactionRepository transactionRepository;
    @Mock HoldingRepository holdingRepository;

    @InjectMocks PortfolioComparisonService service;

    private final UUID userId = UUID.randomUUID();

    private Portfolio portfolio(UUID id, String name) {
        return Portfolio.builder().id(id).userId(userId).name(name).active(true).build();
    }

    private PortfolioSnapshot snap(UUID portfolioId, LocalDate date, String value, String cost) {
        return PortfolioSnapshot.builder()
                .portfolioId(portfolioId)
                .snapshotDate(date)
                .totalValueTry(new BigDecimal(value))
                .totalCostTry(new BigDecimal(cost))
                .build();
    }

    private InvestmentTransaction sell(
            UUID portfolioId, UUID assetId, LocalDate date, String price, String qty) {
        return InvestmentTransaction.builder()
                .portfolioId(portfolioId)
                .assetId(assetId)
                .txnType(InvestmentTransaction.TxnType.SELL)
                .quantity(new BigDecimal(qty))
                .priceTry(new BigDecimal(price))
                .amountTry(new BigDecimal(price).multiply(new BigDecimal(qty)))
                .txnDate(date)
                .build();
    }

    private PortfolioHolding holding(UUID assetId, String avgCost) {
        return PortfolioHolding.builder()
                .assetId(assetId)
                .avgCostTry(new BigDecimal(avgCost))
                .quantity(BigDecimal.ONE)
                .build();
    }

    @Test
    void emptyIdsThrowsBusinessRule() {
        assertThatThrownBy(() -> service.compare(userId, List.of(), null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ids required");
    }

    @Test
    void singleIdHappyPathReturnsTrySeries() {
        UUID p1 = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p1), eq(userId)))
                .thenReturn(Optional.of(portfolio(p1, "Main")));
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(p1))
                .thenReturn(List.of(snap(p1, LocalDate.of(2026, 4, 1), "100", "80")));
        when(transactionRepository
                        .findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
                                eq(p1), eq(InvestmentTransaction.TxnType.SELL), any()))
                .thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(p1)).thenReturn(List.of());

        PortfolioComparisonResponse res = service.compare(userId, List.of(p1), null, null);

        assertThat(res.currency()).isEqualTo("TRY");
        assertThat(res.series()).hasSize(1);
        assertThat(res.series().get(0).portfolioId()).isEqualTo(p1);
        assertThat(res.series().get(0).points()).hasSize(1);
        assertThat(res.series().get(0).points().get(0).totalValueTry()).isEqualByComparingTo("100");
        assertThat(res.series().get(0).points().get(0).unrealizedPnlTry())
                .isEqualByComparingTo("20");
        assertThat(res.series().get(0).points().get(0).realizedPnlTry()).isEqualByComparingTo("0");
    }

    @Test
    void twoIdsRollUpRealisedPnlAcrossThreeDates() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID asset = UUID.randomUUID();

        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p1), eq(userId)))
                .thenReturn(Optional.of(portfolio(p1, "P1")));
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p2), eq(userId)))
                .thenReturn(Optional.of(portfolio(p2, "P2")));

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 1);

        when(snapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        eq(p1), eq(from), eq(to)))
                .thenReturn(
                        List.of(
                                snap(p1, LocalDate.of(2026, 1, 1), "100", "80"),
                                snap(p1, LocalDate.of(2026, 2, 1), "120", "80"),
                                snap(p1, LocalDate.of(2026, 3, 1), "140", "80")));
        when(snapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        eq(p2), eq(from), eq(to)))
                .thenReturn(List.of(snap(p2, LocalDate.of(2026, 1, 15), "200", "150")));

        // P1 has two SELL events at different dates; P2 has none.
        when(transactionRepository
                        .findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
                                eq(p1), eq(InvestmentTransaction.TxnType.SELL), eq(to)))
                .thenReturn(
                        List.of(
                                sell(p1, asset, LocalDate.of(2026, 1, 15), "60", "1"),
                                sell(p1, asset, LocalDate.of(2026, 2, 15), "70", "2")));
        when(transactionRepository
                        .findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
                                eq(p2), eq(InvestmentTransaction.TxnType.SELL), eq(to)))
                .thenReturn(List.of());

        when(holdingRepository.findByPortfolioId(p1)).thenReturn(List.of(holding(asset, "50")));
        when(holdingRepository.findByPortfolioId(p2)).thenReturn(List.of());

        PortfolioComparisonResponse res = service.compare(userId, List.of(p1, p2), from, to);

        assertThat(res.series()).hasSize(2);
        // p1 day 1: no sells yet -> realized 0
        assertThat(res.series().get(0).points().get(0).realizedPnlTry()).isEqualByComparingTo("0");
        // p1 day 2: one sell of 1 @ 60, avg 50 -> realized 10
        assertThat(res.series().get(0).points().get(1).realizedPnlTry()).isEqualByComparingTo("10");
        // p1 day 3: both sells -> 10 + (70 - 50) * 2 = 50
        assertThat(res.series().get(0).points().get(2).realizedPnlTry()).isEqualByComparingTo("50");
        // total = unrealized + realized at day 3 = (140-80) + 50 = 110
        assertThat(res.series().get(0).points().get(2).totalPnlTry()).isEqualByComparingTo("110");
        // p2 has no sells
        assertThat(res.series().get(1).points()).hasSize(1);
        assertThat(res.series().get(1).points().get(0).realizedPnlTry()).isEqualByComparingTo("0");
    }

    @Test
    void ownershipRejectionThrowsResourceNotFound() {
        UUID p1 = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p1), eq(userId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(userId, List.of(p1), null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(p1.toString());
    }

    @Test
    void fromAfterToRejected() {
        UUID p1 = UUID.randomUUID();
        // ownership stub used after range validation in the current order
        lenient()
                .when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p1), eq(userId)))
                .thenReturn(Optional.of(portfolio(p1, "Main")));

        assertThatThrownBy(
                        () ->
                                service.compare(
                                        userId,
                                        List.of(p1),
                                        LocalDate.of(2026, 5, 1),
                                        LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid range");
    }

    @Test
    void allTimeFallbackUsesUnboundedRepositoryQuery() {
        UUID p1 = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p1), eq(userId)))
                .thenReturn(Optional.of(portfolio(p1, "Main")));
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(p1))
                .thenReturn(List.of(snap(p1, LocalDate.of(2026, 4, 1), "100", "80")));
        when(transactionRepository
                        .findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
                                eq(p1), eq(InvestmentTransaction.TxnType.SELL), any()))
                .thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(p1)).thenReturn(List.of());

        PortfolioComparisonResponse res = service.compare(userId, List.of(p1), null, null);

        assertThat(res.series().get(0).points()).hasSize(1);
    }

    @Test
    void onlyFromSetUsesRangeQuery() {
        UUID p1 = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p1), eq(userId)))
                .thenReturn(Optional.of(portfolio(p1, "Main")));
        when(snapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        eq(p1), eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(9999, 12, 31))))
                .thenReturn(List.of(snap(p1, LocalDate.of(2026, 4, 1), "100", "80")));
        when(transactionRepository
                        .findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
                                eq(p1), eq(InvestmentTransaction.TxnType.SELL), any()))
                .thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(p1)).thenReturn(List.of());

        PortfolioComparisonResponse res =
                service.compare(userId, List.of(p1), LocalDate.of(2026, 2, 1), null);

        assertThat(res.series().get(0).points()).hasSize(1);
    }

    @Test
    void inactivePortfolioRejectedAsNotFound() {
        UUID archived = UUID.randomUUID();
        // findByIdAndUserIdAndActiveTrue already filters out inactive rows -> empty optional.
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(archived), eq(userId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(userId, List.of(archived), null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateIdsAreDeduplicatedServerSide() {
        UUID p1 = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p1), eq(userId)))
                .thenReturn(Optional.of(portfolio(p1, "Main")));
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(p1)).thenReturn(List.of());

        PortfolioComparisonResponse res = service.compare(userId, List.of(p1, p1, p1), null, null);

        assertThat(res.series()).hasSize(1);
    }

    @Test
    void maxPortfoliosCapEnforced() {
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) ids.add(UUID.randomUUID());

        assertThatThrownBy(() -> service.compare(userId, ids, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Too many portfolios");
    }

    @Test
    void emptySnapshotListProducesEmptyPointsButPreservesPortfolio() {
        UUID p1 = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(eq(p1), eq(userId)))
                .thenReturn(Optional.of(portfolio(p1, "Main")));
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(p1)).thenReturn(List.of());

        PortfolioComparisonResponse res = service.compare(userId, List.of(p1), null, null);

        assertThat(res.series()).hasSize(1);
        assertThat(res.series().get(0).points()).isEmpty();
        assertThat(res.series().get(0).name()).isEqualTo("Main");
    }
}
