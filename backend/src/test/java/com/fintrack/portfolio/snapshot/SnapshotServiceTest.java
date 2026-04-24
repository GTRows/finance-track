package com.fintrack.portfolio.snapshot;

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
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.snapshot.dto.SnapshotResponse;
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
class SnapshotServiceTest {

    @Mock SnapshotRepository snapshotRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock AssetRepository assetRepository;

    @InjectMocks SnapshotService service;

    private final UUID userId = UUID.randomUUID();

    private Portfolio portfolio() {
        return Portfolio.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("P")
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
                .price(price != null ? new BigDecimal(price) : null)
                .build();
    }

    @Test
    void captureDailyReturnsZerosWhenNoPortfolios() {
        when(portfolioRepository.findAllByActiveTrue()).thenReturn(List.of());

        SnapshotService.CaptureResult res = service.captureDaily();

        assertThat(res.created()).isZero();
        assertThat(res.updated()).isZero();
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void captureDailyCreatesSnapshotWithSummedValueAndCost() {
        Portfolio p = portfolio();
        Asset btc = asset("BTC", "100");
        Asset eth = asset("ETH", "50");
        PortfolioHolding hBtc =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(btc.getId())
                        .quantity(new BigDecimal("2"))
                        .avgCostTry(new BigDecimal("80"))
                        .build();
        PortfolioHolding hEth =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(eth.getId())
                        .quantity(new BigDecimal("3"))
                        .avgCostTry(new BigDecimal("40"))
                        .build();

        when(portfolioRepository.findAllByActiveTrue()).thenReturn(List.of(p));
        when(holdingRepository.findByPortfolioId(p.getId())).thenReturn(List.of(hBtc, hEth));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc, eth));
        when(snapshotRepository.findByPortfolioIdAndSnapshotDate(eq(p.getId()), any()))
                .thenReturn(Optional.empty());

        SnapshotService.CaptureResult res = service.captureDaily();

        assertThat(res.created()).isEqualTo(1);
        assertThat(res.updated()).isZero();
        ArgumentCaptor<PortfolioSnapshot> captor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        PortfolioSnapshot saved = captor.getValue();
        assertThat(saved.getTotalValueTry()).isEqualByComparingTo("350");
        assertThat(saved.getTotalCostTry()).isEqualByComparingTo("280");
        assertThat(saved.getHoldingsJson()).containsKeys("BTC", "ETH");
    }

    @Test
    void captureDailyUpdatesExistingSnapshotInPlace() {
        Portfolio p = portfolio();
        Asset btc = asset("BTC", "200");
        PortfolioHolding h =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(btc.getId())
                        .quantity(new BigDecimal("1"))
                        .avgCostTry(new BigDecimal("100"))
                        .build();
        PortfolioSnapshot existing =
                PortfolioSnapshot.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .snapshotDate(LocalDate.now())
                        .totalValueTry(new BigDecimal("999"))
                        .totalCostTry(new BigDecimal("50"))
                        .build();

        when(portfolioRepository.findAllByActiveTrue()).thenReturn(List.of(p));
        when(holdingRepository.findByPortfolioId(p.getId())).thenReturn(List.of(h));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc));
        when(snapshotRepository.findByPortfolioIdAndSnapshotDate(eq(p.getId()), any()))
                .thenReturn(Optional.of(existing));

        SnapshotService.CaptureResult res = service.captureDaily();

        assertThat(res.created()).isZero();
        assertThat(res.updated()).isEqualTo(1);
        assertThat(existing.getTotalValueTry()).isEqualByComparingTo("200");
        assertThat(existing.getTotalCostTry()).isEqualByComparingTo("100");
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void captureDailyHandlesEmptyHoldings() {
        Portfolio p = portfolio();
        when(portfolioRepository.findAllByActiveTrue()).thenReturn(List.of(p));
        when(holdingRepository.findByPortfolioId(p.getId())).thenReturn(List.of());
        when(snapshotRepository.findByPortfolioIdAndSnapshotDate(eq(p.getId()), any()))
                .thenReturn(Optional.empty());

        service.captureDaily();

        ArgumentCaptor<PortfolioSnapshot> captor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalValueTry()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getTotalCostTry()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getHoldingsJson()).isEmpty();
    }

    @Test
    void captureDailySkipsHoldingsWithMissingAsset() {
        Portfolio p = portfolio();
        Asset btc = asset("BTC", "100");
        PortfolioHolding good =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(btc.getId())
                        .quantity(new BigDecimal("1"))
                        .avgCostTry(new BigDecimal("80"))
                        .build();
        PortfolioHolding orphan =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(UUID.randomUUID())
                        .quantity(new BigDecimal("5"))
                        .avgCostTry(new BigDecimal("10"))
                        .build();

        when(portfolioRepository.findAllByActiveTrue()).thenReturn(List.of(p));
        when(holdingRepository.findByPortfolioId(p.getId())).thenReturn(List.of(good, orphan));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc));
        when(snapshotRepository.findByPortfolioIdAndSnapshotDate(eq(p.getId()), any()))
                .thenReturn(Optional.empty());

        service.captureDaily();

        ArgumentCaptor<PortfolioSnapshot> captor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalValueTry()).isEqualByComparingTo("100");
        assertThat(captor.getValue().getTotalCostTry()).isEqualByComparingTo("80");
    }

    @Test
    void captureDailyTreatsMissingPriceAsZeroValue() {
        Portfolio p = portfolio();
        Asset btc = asset("BTC", null);
        PortfolioHolding h =
                PortfolioHolding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .assetId(btc.getId())
                        .quantity(new BigDecimal("4"))
                        .avgCostTry(new BigDecimal("30"))
                        .build();

        when(portfolioRepository.findAllByActiveTrue()).thenReturn(List.of(p));
        when(holdingRepository.findByPortfolioId(p.getId())).thenReturn(List.of(h));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc));
        when(snapshotRepository.findByPortfolioIdAndSnapshotDate(eq(p.getId()), any()))
                .thenReturn(Optional.empty());

        service.captureDaily();

        ArgumentCaptor<PortfolioSnapshot> captor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalValueTry()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getTotalCostTry()).isEqualByComparingTo("120");
    }

    @Test
    void listForPortfolioRequiresOwnership() {
        UUID portfolioId = UUID.randomUUID();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForPortfolio(userId, portfolioId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listForPortfolioReturnsMappedChronologicalRows() {
        Portfolio p = portfolio();
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(p.getId(), userId))
                .thenReturn(Optional.of(p));
        PortfolioSnapshot s1 =
                PortfolioSnapshot.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(p.getId())
                        .snapshotDate(LocalDate.of(2026, 4, 1))
                        .totalValueTry(new BigDecimal("100"))
                        .totalCostTry(new BigDecimal("80"))
                        .build();
        when(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(p.getId()))
                .thenReturn(List.of(s1));

        List<SnapshotResponse> res = service.listForPortfolio(userId, p.getId());

        assertThat(res).hasSize(1);
        assertThat(res.get(0).totalValueTry()).isEqualByComparingTo("100");
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
