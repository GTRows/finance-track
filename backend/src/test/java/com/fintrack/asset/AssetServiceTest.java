package com.fintrack.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.dto.AssetResponse;
import com.fintrack.common.entity.Asset;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock AssetRepository assetRepository;

    @InjectMocks AssetService service;

    private Asset asset(String symbol, Asset.AssetType type) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol)
                .assetType(type)
                .currency("USD")
                .price(new BigDecimal("1"))
                .priceUsd(new BigDecimal("1"))
                .priceUpdatedAt(Instant.parse("2026-04-01T00:00:00Z"))
                .build();
    }

    @Test
    void listAllNullTypeFetchesAllAssets() {
        Asset btc = asset("BTC", Asset.AssetType.CRYPTO);
        when(assetRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(btc));

        List<AssetResponse> res = service.listAll(null);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).symbol()).isEqualTo("BTC");
        verify(assetRepository).findAllByOrderBySymbolAsc();
    }

    @Test
    void listAllByTypeFiltersByAssetType() {
        Asset stk = asset("AAPL", Asset.AssetType.STOCK);
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.STOCK))
                .thenReturn(List.of(stk));

        List<AssetResponse> res = service.listAll(Asset.AssetType.STOCK);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).assetType()).isEqualTo(Asset.AssetType.STOCK);
        verify(assetRepository).findByAssetTypeOrderBySymbolAsc(Asset.AssetType.STOCK);
    }

    @Test
    void findByIdMapsEntityToResponse() {
        Asset btc = asset("BTC", Asset.AssetType.CRYPTO);
        when(assetRepository.findById(btc.getId())).thenReturn(Optional.of(btc));

        Optional<AssetResponse> res = service.findById(btc.getId());

        assertThat(res).isPresent();
        assertThat(res.get().symbol()).isEqualTo("BTC");
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findById(id)).isEmpty();
    }

    @Test
    void listAllReturnsEmptyWhenRepositoryEmpty() {
        when(assetRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of());

        assertThat(service.listAll(null)).isEmpty();
    }
}
