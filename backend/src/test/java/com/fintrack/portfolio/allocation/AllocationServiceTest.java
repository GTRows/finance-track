package com.fintrack.portfolio.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioAllocationTarget;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.allocation.dto.AllocationRow;
import com.fintrack.portfolio.allocation.dto.AllocationSummary;
import com.fintrack.portfolio.allocation.dto.AllocationTargetInput;
import com.fintrack.portfolio.allocation.dto.SetAllocationRequest;
import com.fintrack.portfolio.holding.HoldingRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllocationServiceTest {

    @Mock AllocationTargetRepository targetRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock AssetRepository assetRepository;

    @InjectMocks AllocationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID portfolioId = UUID.randomUUID();

    private void stubOwnership() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(
                        Optional.of(
                                Portfolio.builder()
                                        .id(portfolioId)
                                        .userId(userId)
                                        .name("P")
                                        .active(true)
                                        .build()));
    }

    private Asset asset(String symbol, AssetType type, String price) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol)
                .assetType(type)
                .currency("TRY")
                .price(new BigDecimal(price))
                .build();
    }

    private PortfolioHolding holding(UUID assetId, String qty) {
        return PortfolioHolding.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .assetId(assetId)
                .quantity(new BigDecimal(qty))
                .avgCostTry(BigDecimal.ZERO)
                .build();
    }

    private PortfolioAllocationTarget target(AssetType type, String percent) {
        return PortfolioAllocationTarget.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .assetType(type)
                .targetPercent(new BigDecimal(percent))
                .build();
    }

    @Test
    void summarizeThrowsWhenPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summarize(userId, portfolioId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void summarizeReturnsEmptyUnconfiguredWhenNoTargetsOrHoldings() {
        stubOwnership();
        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());

        AllocationSummary res = service.summarize(userId, portfolioId);

        assertThat(res.totalValueTry()).isEqualByComparingTo("0");
        assertThat(res.configured()).isFalse();
        assertThat(res.rows()).isEmpty();
    }

    @Test
    void summarizeComputesActualPercentAndDriftAgainstTargets() {
        stubOwnership();
        Asset btc = asset("BTC", AssetType.CRYPTO, "100");
        Asset gold = asset("GOLD", AssetType.GOLD, "50");
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(target(AssetType.CRYPTO, "70"), target(AssetType.GOLD, "30")));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(holding(btc.getId(), "6"), holding(gold.getId(), "8")));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc, gold));

        AllocationSummary res = service.summarize(userId, portfolioId);

        assertThat(res.totalValueTry()).isEqualByComparingTo("1000");
        assertThat(res.configured()).isTrue();
        assertThat(res.rows()).hasSize(2);
        AllocationRow crypto =
                res.rows().stream()
                        .filter(r -> r.assetType() == AssetType.CRYPTO)
                        .findFirst()
                        .orElseThrow();
        assertThat(crypto.actualValueTry()).isEqualByComparingTo("600.00");
        assertThat(crypto.actualPercent()).isEqualByComparingTo("60.00");
        assertThat(crypto.targetPercent()).isEqualByComparingTo("70.00");
        assertThat(crypto.driftPercent()).isEqualByComparingTo("-10.00");
        assertThat(crypto.driftValueTry()).isEqualByComparingTo("-100.00");
    }

    @Test
    void summarizeIncludesTypeThatIsOnlyInTargetsNotHoldings() {
        stubOwnership();
        Asset btc = asset("BTC", AssetType.CRYPTO, "100");
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(target(AssetType.CRYPTO, "60"), target(AssetType.STOCK, "40")));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(holding(btc.getId(), "10")));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc));

        AllocationSummary res = service.summarize(userId, portfolioId);

        AllocationRow stock =
                res.rows().stream()
                        .filter(r -> r.assetType() == AssetType.STOCK)
                        .findFirst()
                        .orElseThrow();
        assertThat(stock.actualValueTry()).isEqualByComparingTo("0");
        assertThat(stock.actualPercent()).isEqualByComparingTo("0");
        assertThat(stock.targetPercent()).isEqualByComparingTo("40.00");
        assertThat(stock.driftPercent()).isEqualByComparingTo("-40.00");
    }

    @Test
    void summarizeIncludesTypeThatIsOnlyInHoldingsWithNoTarget() {
        stubOwnership();
        Asset btc = asset("BTC", AssetType.CRYPTO, "200");
        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(holding(btc.getId(), "5")));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc));

        AllocationSummary res = service.summarize(userId, portfolioId);

        assertThat(res.configured()).isFalse();
        assertThat(res.rows()).hasSize(1);
        AllocationRow row = res.rows().get(0);
        assertThat(row.targetPercent()).isEqualByComparingTo("0");
        assertThat(row.actualPercent()).isEqualByComparingTo("100.00");
        assertThat(row.driftPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void summarizeSkipsHoldingsWithMissingAssetOrNullPriceOrNullQuantity() {
        stubOwnership();
        Asset btc = asset("BTC", AssetType.CRYPTO, "100");
        Asset priceless =
                Asset.builder()
                        .id(UUID.randomUUID())
                        .symbol("NP")
                        .name("NP")
                        .assetType(AssetType.CRYPTO)
                        .currency("TRY")
                        .price(null)
                        .build();
        PortfolioHolding good = holding(btc.getId(), "3");
        PortfolioHolding priceNull = holding(priceless.getId(), "5");
        PortfolioHolding qtyNull =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(btc.getId())
                        .quantity(null)
                        .avgCostTry(BigDecimal.ZERO)
                        .build();
        PortfolioHolding orphan =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(UUID.randomUUID())
                        .quantity(new BigDecimal("7"))
                        .avgCostTry(BigDecimal.ZERO)
                        .build();

        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(good, priceNull, qtyNull, orphan));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc, priceless));

        AllocationSummary res = service.summarize(userId, portfolioId);

        assertThat(res.totalValueTry()).isEqualByComparingTo("300");
    }

    @Test
    void replaceTargetsPersistsAllWithNonZeroPercent() {
        stubOwnership();
        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());

        SetAllocationRequest req =
                new SetAllocationRequest(
                        List.of(
                                new AllocationTargetInput(AssetType.CRYPTO, new BigDecimal("50")),
                                new AllocationTargetInput(AssetType.STOCK, new BigDecimal("50"))));

        service.replaceTargets(userId, portfolioId, req);

        verify(targetRepository).deleteByPortfolioId(portfolioId);
        verify(targetRepository, times(2)).save(any(PortfolioAllocationTarget.class));
    }

    @Test
    void replaceTargetsSkipsZeroEntries() {
        stubOwnership();
        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());

        SetAllocationRequest req =
                new SetAllocationRequest(
                        List.of(
                                new AllocationTargetInput(AssetType.CRYPTO, new BigDecimal("100")),
                                new AllocationTargetInput(AssetType.STOCK, BigDecimal.ZERO)));

        service.replaceTargets(userId, portfolioId, req);

        verify(targetRepository, times(1)).save(any(PortfolioAllocationTarget.class));
    }

    @Test
    void replaceTargetsRejectsDuplicateAssetType() {
        stubOwnership();
        SetAllocationRequest req =
                new SetAllocationRequest(
                        List.of(
                                new AllocationTargetInput(AssetType.CRYPTO, new BigDecimal("50")),
                                new AllocationTargetInput(AssetType.CRYPTO, new BigDecimal("50"))));

        assertThatThrownBy(() -> service.replaceTargets(userId, portfolioId, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Duplicate");

        verify(targetRepository, never()).deleteByPortfolioId(any());
    }

    @Test
    void replaceTargetsRejectsSumNotEqualToHundred() {
        stubOwnership();
        SetAllocationRequest req =
                new SetAllocationRequest(
                        List.of(
                                new AllocationTargetInput(AssetType.CRYPTO, new BigDecimal("60")),
                                new AllocationTargetInput(AssetType.STOCK, new BigDecimal("30"))));

        assertThatThrownBy(() -> service.replaceTargets(userId, portfolioId, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sum to 100");

        verify(targetRepository, never()).save(any());
    }

    @Test
    void replaceTargetsAllowsSumWithinTolerance() {
        stubOwnership();
        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());

        SetAllocationRequest req =
                new SetAllocationRequest(
                        List.of(
                                new AllocationTargetInput(
                                        AssetType.CRYPTO, new BigDecimal("49.98")),
                                new AllocationTargetInput(
                                        AssetType.STOCK, new BigDecimal("50.03"))));

        service.replaceTargets(userId, portfolioId, req);

        verify(targetRepository, times(2)).save(any(PortfolioAllocationTarget.class));
    }

    @Test
    void replaceTargetsAcceptsEmptyListAsClear() {
        stubOwnership();
        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());

        service.replaceTargets(userId, portfolioId, new SetAllocationRequest(List.of()));

        verify(targetRepository).deleteByPortfolioId(portfolioId);
        verify(targetRepository, never()).save(any());
    }

    @Test
    void replaceTargetsRequiresPortfolioOwnership() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        SetAllocationRequest req =
                new SetAllocationRequest(
                        List.of(
                                new AllocationTargetInput(
                                        AssetType.CRYPTO, new BigDecimal("100"))));

        assertThatThrownBy(() -> service.replaceTargets(userId, portfolioId, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rowsAreSortedByAssetTypeEnumOrder() {
        stubOwnership();
        Asset eth = asset("ETH", AssetType.CRYPTO, "10");
        Asset xu = asset("XU100", AssetType.STOCK, "10");
        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(holding(xu.getId(), "1"), holding(eth.getId(), "1")));
        when(assetRepository.findAllById(any())).thenReturn(List.of(eth, xu));

        AllocationSummary res = service.summarize(userId, portfolioId);

        List<AssetType> observed = res.rows().stream().map(AllocationRow::assetType).toList();
        Map<AssetType, Integer> rank = Map.of(observed.get(0), 0, observed.get(1), 1);
        assertThat(observed.get(0).ordinal()).isLessThan(observed.get(1).ordinal());
        assertThat(rank).hasSize(2);
    }
}
