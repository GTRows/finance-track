package com.fintrack.portfolio.transaction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.transaction.dto.RecordTransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentTransactionServiceAuditTest {

    @Mock InvestmentTransactionRepository transactionRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock AssetRepository assetRepository;
    @Mock AuditService auditService;

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

    private RecordTransactionRequest req(TxnType type, String qty, String price) {
        return new RecordTransactionRequest(
                assetId,
                type,
                new BigDecimal(qty),
                new BigDecimal(price),
                BigDecimal.ZERO,
                LocalDate.of(2026, 4, 1),
                "note");
    }

    @Test
    void recordEmitsCreatedAudit() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            InvestmentTransaction t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });

        service.record(userId, portfolioId, req(TxnType.BUY, "1", "100"));

        verify(auditService)
                .success(
                        eq(AuditAction.INVESTMENT_TRANSACTION_CREATED),
                        eq(userId),
                        any(),
                        contains("id="));
    }

    @Test
    void recordEmitsFailureAuditOnSellWithoutHolding() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            InvestmentTransaction t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });

        assertThatThrownBy(() -> service.record(userId, portfolioId, req(TxnType.SELL, "1", "100")))
                .isInstanceOf(BusinessRuleException.class);

        verify(auditService)
                .failure(
                        eq(AuditAction.INVESTMENT_TRANSACTION_CREATED),
                        eq(userId),
                        any(),
                        contains("not in the portfolio"));
    }

    @Test
    void recordEmitsFailureAuditOnSellQuantityExceeds() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset()));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId))
                .thenReturn(
                        Optional.of(
                                com.fintrack.common.entity.PortfolioHolding.builder()
                                        .portfolioId(portfolioId)
                                        .assetId(assetId)
                                        .quantity(new BigDecimal("1"))
                                        .avgCostTry(new BigDecimal("100"))
                                        .build()));
        when(transactionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            InvestmentTransaction t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });

        assertThatThrownBy(() -> service.record(userId, portfolioId, req(TxnType.SELL, "5", "100")))
                .isInstanceOf(BusinessRuleException.class);

        verify(auditService)
                .failure(
                        eq(AuditAction.INVESTMENT_TRANSACTION_CREATED),
                        eq(userId),
                        any(),
                        contains("exceeds"));
    }

    @Test
    void deleteEmitsDeletedAudit() {
        UUID txnId = UUID.randomUUID();
        InvestmentTransaction txn =
                InvestmentTransaction.builder()
                        .id(txnId)
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .txnType(TxnType.BUY)
                        .quantity(BigDecimal.ONE)
                        .priceTry(BigDecimal.ONE)
                        .amountTry(BigDecimal.ONE)
                        .txnDate(LocalDate.now())
                        .build();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(transactionRepository.findByIdAndPortfolioId(txnId, portfolioId))
                .thenReturn(Optional.of(txn));

        service.delete(userId, portfolioId, txnId);

        verify(auditService)
                .success(
                        eq(AuditAction.INVESTMENT_TRANSACTION_DELETED),
                        eq(userId),
                        any(),
                        contains(txnId.toString()));
    }
}
