package com.fintrack.portfolio.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.dto.AddHoldingRequest;
import com.fintrack.portfolio.holding.dto.HoldingResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock HoldingRepository holdingRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock AssetRepository assetRepository;

    @InjectMocks HoldingService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    private Portfolio ownedPortfolio() {
        return Portfolio.builder().id(portfolioId).userId(userId).name("Main").active(true).build();
    }

    private Asset asset(String symbol) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol + " Name")
                .assetType(AssetType.CRYPTO)
                .currency("TRY")
                .build();
    }

    @Test
    void listReturnsEmptyWhenNoHoldings() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());

        assertThat(service.listForPortfolio(userId, portfolioId)).isEmpty();
    }

    @Test
    void listSortsPinnedFirstThenBySymbol() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));

        Asset btc = asset("BTC");
        Asset eth = asset("ETH");
        Asset ada = asset("ADA");

        PortfolioHolding hBtc =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(btc.getId())
                        .quantity(BigDecimal.ONE)
                        .avgCostTry(BigDecimal.TEN)
                        .pinned(false)
                        .build();
        PortfolioHolding hEth =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(eth.getId())
                        .quantity(BigDecimal.ONE)
                        .avgCostTry(BigDecimal.TEN)
                        .pinned(true)
                        .build();
        PortfolioHolding hAda =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(ada.getId())
                        .quantity(BigDecimal.ONE)
                        .avgCostTry(BigDecimal.TEN)
                        .pinned(false)
                        .build();

        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(hBtc, hEth, hAda));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc, eth, ada));

        List<HoldingResponse> res = service.listForPortfolio(userId, portfolioId);

        assertThat(res)
                .extracting(HoldingResponse::assetSymbol)
                .containsExactly("ETH", "ADA", "BTC");
    }

    @Test
    void listThrowsIfAssetMissingForHolding() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        PortfolioHolding orphan =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(UUID.randomUUID())
                        .quantity(BigDecimal.ONE)
                        .avgCostTry(BigDecimal.TEN)
                        .build();
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(orphan));
        when(assetRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.listForPortfolio(userId, portfolioId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Asset not found");
    }

    @Test
    void addSavesNewHolding() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        Asset btc = asset("BTC");
        when(assetRepository.findById(btc.getId())).thenReturn(Optional.of(btc));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, btc.getId()))
                .thenReturn(Optional.empty());
        when(holdingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HoldingResponse res =
                service.add(
                        userId,
                        portfolioId,
                        new AddHoldingRequest(
                                btc.getId(), new BigDecimal("2"), new BigDecimal("100")));

        assertThat(res.assetSymbol()).isEqualTo("BTC");
        assertThat(res.quantity()).isEqualByComparingTo("2");
    }

    @Test
    void addRejectsDuplicateAsset() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        Asset btc = asset("BTC");
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
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in the portfolio");

        verify(holdingRepository, never()).save(any());
    }

    @Test
    void addRejectsUnknownAsset() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        UUID assetId = UUID.randomUUID();
        when(assetRepository.findById(assetId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.add(
                                        userId,
                                        portfolioId,
                                        new AddHoldingRequest(
                                                assetId, BigDecimal.ONE, BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void togglePinFlipsFlag() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        Asset btc = asset("BTC");
        PortfolioHolding h =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(btc.getId())
                        .quantity(BigDecimal.ONE)
                        .avgCostTry(BigDecimal.TEN)
                        .pinned(false)
                        .build();
        when(holdingRepository.findByIdAndPortfolioId(h.getId(), portfolioId))
                .thenReturn(Optional.of(h));
        when(assetRepository.findById(btc.getId())).thenReturn(Optional.of(btc));

        HoldingResponse res = service.togglePin(userId, portfolioId, h.getId());

        assertThat(res.pinned()).isTrue();
        assertThat(h.isPinned()).isTrue();
    }

    @Test
    void togglePinThrowsWhenHoldingNotFound() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        when(holdingRepository.findByIdAndPortfolioId(id, portfolioId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.togglePin(userId, portfolioId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesOwnedHolding() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.of(ownedPortfolio()));
        PortfolioHolding h =
                PortfolioHolding.builder()
                        .id(id)
                        .portfolioId(portfolioId)
                        .assetId(UUID.randomUUID())
                        .quantity(BigDecimal.ONE)
                        .avgCostTry(BigDecimal.TEN)
                        .build();
        when(holdingRepository.findByIdAndPortfolioId(id, portfolioId)).thenReturn(Optional.of(h));

        service.delete(userId, portfolioId, id);

        verify(holdingRepository).delete(h);
    }

    @Test
    void deleteThrowsWhenNotOwned() {
        UUID id = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, portfolioId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
