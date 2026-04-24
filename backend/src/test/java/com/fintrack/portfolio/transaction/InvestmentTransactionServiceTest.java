package com.fintrack.portfolio.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.transaction.dto.RecordTransactionRequest;
import com.fintrack.portfolio.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentTransactionServiceTest {

    @Mock InvestmentTransactionRepository transactionRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock AssetRepository assetRepository;

    @InjectMocks InvestmentTransactionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    private Portfolio ownedPortfolio() {
        return Portfolio.builder().id(portfolioId).userId(userId).name("Main").active(true).build();
    }

    private Asset asset() {
        return Asset.builder().id(assetId).symbol("BTC").name("Bitcoin").build();
    }

    private RecordTransactionRequest req(TxnType type, String qty, String price, String fee) {
        return new RecordTransactionRequest(
                assetId,
                type,
                new BigDecimal(qty),
                new BigDecimal(price),
                fee == null ? null : new BigDecimal(fee),
                LocalDate.of(2026, 4, 1),
                "note");
    }

    @Test
    void recordBuyCreatesNewHoldingWithAvgCostIncludingFee() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse res =
                service.record(userId, portfolioId, req(TxnType.BUY, "2", "100", "10"));

        assertThat(res.amountTry()).isEqualByComparingTo("210");
        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);
        verify(holdingRepository).save(captor.capture());
        PortfolioHolding saved = captor.getValue();
        assertThat(saved.getQuantity()).isEqualByComparingTo("2");
        assertThat(saved.getAvgCostTry()).isEqualByComparingTo("105.0000");
    }

    @Test
    void recordBuyMergesIntoExistingHoldingUsingWeightedAverageCost() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        PortfolioHolding existing =
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("1"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record(userId, portfolioId, req(TxnType.BUY, "1", "200", "0"));

        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);
        verify(holdingRepository).save(captor.capture());
        PortfolioHolding saved = captor.getValue();
        assertThat(saved.getQuantity()).isEqualByComparingTo("2");
        assertThat(saved.getAvgCostTry()).isEqualByComparingTo("150.0000");
    }

    @Test
    void recordSellReducesQuantityAndKeepsAvgCost() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        PortfolioHolding existing =
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("5"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse res =
                service.record(userId, portfolioId, req(TxnType.SELL, "2", "150", "5"));

        assertThat(res.amountTry()).isEqualByComparingTo("295");
        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);
        verify(holdingRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("3");
        assertThat(captor.getValue().getAvgCostTry()).isEqualByComparingTo("100");
    }

    @Test
    void recordSellDeletesHoldingWhenQuantityReachesZero() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        PortfolioHolding existing =
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("2"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record(userId, portfolioId, req(TxnType.SELL, "2", "120", null));

        verify(holdingRepository).delete(existing);
        verify(holdingRepository, never()).save(any());
    }

    @Test
    void recordSellBeyondHoldingThrowsBusinessRule() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        PortfolioHolding existing =
                PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("1"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(
                        () ->
                                service.record(
                                        userId, portfolioId, req(TxnType.SELL, "5", "120", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void recordSellWithoutHoldingThrowsBusinessRule() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(
                        () ->
                                service.record(
                                        userId, portfolioId, req(TxnType.SELL, "1", "120", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in the portfolio");
    }

    @Test
    void depositAndWithdrawDoNotTouchHoldings() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record(userId, portfolioId, req(TxnType.DEPOSIT, "1", "1000", null));
        service.record(userId, portfolioId, req(TxnType.WITHDRAW, "1", "500", null));

        verify(holdingRepository, never()).save(any());
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void besContributionBehavesLikeBuy() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.record(userId, portfolioId, req(TxnType.BES_CONTRIBUTION, "10", "5", "0"));

        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);
        verify(holdingRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("10");
        assertThat(captor.getValue().getAvgCostTry()).isEqualByComparingTo("5.0000");
    }

    @Test
    void recordOnNonOwnedPortfolioThrows() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.record(userId, portfolioId, req(TxnType.BUY, "1", "1", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deleteRequiresOwnershipAndExistingTxn() {
        UUID txnId = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(transactionRepository.findByIdAndPortfolioId(txnId, portfolioId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, portfolioId, txnId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteSucceedsWhenOwnedAndFound() {
        UUID txnId = UUID.randomUUID();
        InvestmentTransaction txn =
                InvestmentTransaction.builder()
                        .id(txnId)
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .txnType(TxnType.BUY)
                        .quantity(new BigDecimal("1"))
                        .priceTry(new BigDecimal("1"))
                        .amountTry(new BigDecimal("1"))
                        .txnDate(LocalDate.now())
                        .build();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(transactionRepository.findByIdAndPortfolioId(txnId, portfolioId))
                .thenReturn(Optional.of(txn));

        service.delete(userId, portfolioId, txnId);

        verify(transactionRepository).delete(txn);
    }

    @Test
    void listReturnsEmptyWhenNoTransactions() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(transactionRepository.findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(portfolioId))
                .thenReturn(List.of());

        assertThat(service.list(userId, portfolioId)).isEmpty();
    }
}
