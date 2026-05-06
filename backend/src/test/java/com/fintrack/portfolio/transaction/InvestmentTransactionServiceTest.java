package com.fintrack.portfolio.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.event.InvestmentTransactionDeletedEvent;
import com.fintrack.common.event.InvestmentTransactionRecordedEvent;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class InvestmentTransactionServiceTest {

    @Mock InvestmentTransactionRepository transactionRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock AssetRepository assetRepository;
    @Mock AuditService auditService;
    @Mock ApplicationEventPublisher eventPublisher;

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
    void recordBuyComputesAmountAndPublishesRecordedEvent() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(transactionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            InvestmentTransaction t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });

        TransactionResponse res =
                service.record(userId, portfolioId, req(TxnType.BUY, "2", "100", "10"));

        assertThat(res.amountTry()).isEqualByComparingTo("210");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InvestmentTransactionRecordedEvent.class);
        InvestmentTransactionRecordedEvent ev =
                (InvestmentTransactionRecordedEvent) captor.getValue();
        assertThat(ev.userId()).isEqualTo(userId);
        assertThat(ev.portfolioId()).isEqualTo(portfolioId);
        assertThat(ev.assetId()).isEqualTo(assetId);
        assertThat(ev.txnType()).isEqualTo(TxnType.BUY);
        assertThat(ev.quantity()).isEqualByComparingTo("2");
        assertThat(ev.priceTry()).isEqualByComparingTo("100");
        assertThat(ev.feeTry()).isEqualByComparingTo("10");
    }

    @Test
    void recordSellComputesAmountSubtractingFeeAndPublishesEvent() {
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
        when(transactionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            InvestmentTransaction t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });

        TransactionResponse res =
                service.record(userId, portfolioId, req(TxnType.SELL, "2", "150", "5"));

        assertThat(res.amountTry()).isEqualByComparingTo("295");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InvestmentTransactionRecordedEvent.class);
    }

    @Test
    void recordSellBeyondHoldingThrowsBusinessRuleAndDoesNotPublish() {
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

        assertThatThrownBy(
                        () ->
                                service.record(
                                        userId, portfolioId, req(TxnType.SELL, "5", "120", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds");

        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordSellWithoutHoldingThrowsBusinessRuleAndDoesNotPublish() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.record(
                                        userId, portfolioId, req(TxnType.SELL, "1", "120", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in the portfolio");

        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void depositAndWithdrawDoNotTouchHoldings() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(transactionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            InvestmentTransaction t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });

        service.record(userId, portfolioId, req(TxnType.DEPOSIT, "1", "1000", null));
        service.record(userId, portfolioId, req(TxnType.WITHDRAW, "1", "500", null));

        verify(holdingRepository, never()).save(any());
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void besContributionPublishesEvent() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(transactionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            InvestmentTransaction t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });

        service.record(userId, portfolioId, req(TxnType.BES_CONTRIBUTION, "10", "5", "0"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InvestmentTransactionRecordedEvent.class);
        InvestmentTransactionRecordedEvent ev =
                (InvestmentTransactionRecordedEvent) captor.getValue();
        assertThat(ev.txnType()).isEqualTo(TxnType.BES_CONTRIBUTION);
    }

    @Test
    void recordOnNonOwnedPortfolioThrows() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.record(userId, portfolioId, req(TxnType.BUY, "1", "1", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
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

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteSucceedsWhenOwnedAndPublishesDeletedEvent() {
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
                        .feeTry(BigDecimal.ZERO)
                        .txnDate(LocalDate.now())
                        .build();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(transactionRepository.findByIdAndPortfolioId(txnId, portfolioId))
                .thenReturn(Optional.of(txn));

        service.delete(userId, portfolioId, txnId);

        verify(transactionRepository).delete(txn);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InvestmentTransactionDeletedEvent.class);
        InvestmentTransactionDeletedEvent ev =
                (InvestmentTransactionDeletedEvent) captor.getValue();
        assertThat(ev.transactionId()).isEqualTo(txnId);
        assertThat(ev.portfolioId()).isEqualTo(portfolioId);
        assertThat(ev.assetId()).isEqualTo(assetId);
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
