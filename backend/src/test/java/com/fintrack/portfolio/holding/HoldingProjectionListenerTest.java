package com.fintrack.portfolio.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.event.InvestmentTransactionDeletedEvent;
import com.fintrack.common.event.InvestmentTransactionRecordedEvent;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingProjectionListenerTest {

    @Mock HoldingRepository holdingRepository;

    @InjectMocks HoldingProjectionListener listener;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();
    private final UUID txnId = UUID.randomUUID();

    private InvestmentTransactionRecordedEvent event(
            TxnType type, String qty, String price, String fee) {
        return new InvestmentTransactionRecordedEvent(
                userId,
                portfolioId,
                assetId,
                txnId,
                type,
                new BigDecimal(qty),
                new BigDecimal(price),
                fee == null ? BigDecimal.ZERO : new BigDecimal(fee),
                null,
                null);
    }

    @Test
    void buyCreatesNewHoldingWithAvgCostIncludingFee() {
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());

        listener.onTransactionRecorded(event(TxnType.BUY, "2", "100", "10"));

        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);
        verify(holdingRepository).save(captor.capture());
        PortfolioHolding saved = captor.getValue();
        assertThat(saved.getQuantity()).isEqualByComparingTo("2");
        assertThat(saved.getAvgCostTry()).isEqualByComparingTo("105.0000");
    }

    @Test
    void buyMergesIntoExistingHoldingUsingWeightedAverageCost() {
        PortfolioHolding existing =
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("1"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.of(existing));

        listener.onTransactionRecorded(event(TxnType.BUY, "1", "200", "0"));

        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);
        verify(holdingRepository).save(captor.capture());
        PortfolioHolding saved = captor.getValue();
        assertThat(saved.getQuantity()).isEqualByComparingTo("2");
        assertThat(saved.getAvgCostTry()).isEqualByComparingTo("150.0000");
    }

    @Test
    void besContributionBehavesLikeBuy() {
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());

        listener.onTransactionRecorded(event(TxnType.BES_CONTRIBUTION, "10", "5", "0"));

        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);
        verify(holdingRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("10");
        assertThat(captor.getValue().getAvgCostTry()).isEqualByComparingTo("5.0000");
    }

    @Test
    void sellDeletesHoldingWhenQuantityReachesZero() {
        PortfolioHolding existing =
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("2"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.of(existing));

        listener.onTransactionRecorded(event(TxnType.SELL, "2", "120", null));

        verify(holdingRepository).delete(existing);
        verify(holdingRepository, never()).save(any());
    }

    @Test
    void sellPartialReducesQuantityAndKeepsAvgCost() {
        PortfolioHolding existing =
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("5"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.of(existing));

        listener.onTransactionRecorded(event(TxnType.SELL, "2", "150", "5"));

        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);
        verify(holdingRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("3");
        assertThat(captor.getValue().getAvgCostTry()).isEqualByComparingTo("100");
    }

    @Test
    void sellWithNoExistingHoldingIsNoOp() {
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());

        listener.onTransactionRecorded(event(TxnType.SELL, "1", "120", null));

        verify(holdingRepository, never()).save(any());
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void sellExceedingHoldingIsNoOp() {
        PortfolioHolding existing =
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("1"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.of(existing));

        listener.onTransactionRecorded(event(TxnType.SELL, "5", "120", null));

        verify(holdingRepository, never()).save(any());
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void depositAndWithdrawAreEarlyReturn() {
        listener.onTransactionRecorded(event(TxnType.DEPOSIT, "1", "1000", null));
        listener.onTransactionRecorded(event(TxnType.WITHDRAW, "1", "500", null));
        listener.onTransactionRecorded(event(TxnType.REBALANCE, "1", "10", null));

        verifyNoInteractions(holdingRepository);
    }

    @Test
    void deletedEventIsNoOp() {
        InvestmentTransactionDeletedEvent deletedEvent =
                new InvestmentTransactionDeletedEvent(
                        userId,
                        portfolioId,
                        assetId,
                        txnId,
                        TxnType.BUY,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        null);

        listener.onTransactionDeleted(deletedEvent);

        verifyNoInteractions(holdingRepository);
    }
}
