package com.fintrack.portfolio.holding;

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
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.dto.AddHoldingRequest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingServiceAuditTest {

    @Mock HoldingRepository holdingRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock AssetRepository assetRepository;
    @Mock AuditService auditService;

    @InjectMocks HoldingService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    private Portfolio ownedPortfolio() {
        return Portfolio.builder().id(portfolioId).userId(userId).name("Main").active(true).build();
    }

    private Asset asset() {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol("BTC")
                .name("BTC")
                .assetType(AssetType.CRYPTO)
                .currency("TRY")
                .build();
    }

    @Test
    void addEmitsCreatedAudit() {
        Asset btc = asset();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(btc.getId())).thenReturn(Optional.of(btc));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, btc.getId()))
                .thenReturn(Optional.empty());
        when(holdingRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            PortfolioHolding h = inv.getArgument(0);
                            h.setId(UUID.randomUUID());
                            return h;
                        });

        service.add(
                userId,
                portfolioId,
                new AddHoldingRequest(btc.getId(), BigDecimal.ONE, BigDecimal.TEN));

        verify(auditService)
                .success(eq(AuditAction.HOLDING_CREATED), eq(userId), any(), contains("id="));
    }

    @Test
    void addEmitsFailureAuditOnDuplicate() {
        Asset btc = asset();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(assetRepository.findById(btc.getId())).thenReturn(Optional.of(btc));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, btc.getId()))
                .thenReturn(
                        Optional.of(
                                PortfolioHolding.builder()
                                        .id(UUID.randomUUID())
                                        .portfolioId(portfolioId)
                                        .assetId(btc.getId())
                                        .quantity(BigDecimal.ONE)
                                        .avgCostTry(BigDecimal.TEN)
                                        .build()));

        assertThatThrownBy(
                        () ->
                                service.add(
                                        userId,
                                        portfolioId,
                                        new AddHoldingRequest(
                                                btc.getId(), BigDecimal.ONE, BigDecimal.TEN)))
                .isInstanceOf(BusinessRuleException.class);

        verify(auditService)
                .failure(
                        eq(AuditAction.HOLDING_CREATED),
                        eq(userId),
                        any(),
                        contains("already in the portfolio"));
    }

    @Test
    void togglePinEmitsUpdatedAudit() {
        Asset btc = asset();
        UUID holdingId = UUID.randomUUID();
        PortfolioHolding holding =
                PortfolioHolding.builder()
                        .id(holdingId)
                        .portfolioId(portfolioId)
                        .assetId(btc.getId())
                        .quantity(BigDecimal.ONE)
                        .avgCostTry(BigDecimal.TEN)
                        .pinned(false)
                        .build();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(holdingRepository.findByIdAndPortfolioId(holdingId, portfolioId))
                .thenReturn(Optional.of(holding));
        when(assetRepository.findById(btc.getId())).thenReturn(Optional.of(btc));

        service.togglePin(userId, portfolioId, holdingId);

        verify(auditService)
                .success(
                        eq(AuditAction.HOLDING_UPDATED),
                        eq(userId),
                        any(),
                        contains(holdingId.toString()));
    }

    @Test
    void deleteEmitsDeletedAudit() {
        UUID holdingId = UUID.randomUUID();
        PortfolioHolding holding =
                PortfolioHolding.builder()
                        .id(holdingId)
                        .portfolioId(portfolioId)
                        .assetId(UUID.randomUUID())
                        .quantity(BigDecimal.ONE)
                        .avgCostTry(BigDecimal.TEN)
                        .build();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(holdingRepository.findByIdAndPortfolioId(holdingId, portfolioId))
                .thenReturn(Optional.of(holding));

        service.delete(userId, portfolioId, holdingId);

        verify(auditService)
                .success(
                        eq(AuditAction.HOLDING_DELETED),
                        eq(userId),
                        any(),
                        contains(holdingId.toString()));
    }
}
