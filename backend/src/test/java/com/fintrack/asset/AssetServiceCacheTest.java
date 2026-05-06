package com.fintrack.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.Asset;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AssetServiceCacheTest.TestConfig.class)
class AssetServiceCacheTest {

    @EnableCaching
    @Import({CacheConfig.class, AssetService.class})
    static class TestConfig {}

    @MockBean AssetRepository assetRepository;

    @Autowired AssetService service;
    @Autowired CacheManager cacheManager;

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

    @BeforeEach
    void clearCaches() {
        cacheManager.getCache(CacheConfig.ASSETS_CACHE).clear();
    }

    @Test
    void listAllSecondCallServedFromCache() {
        when(assetRepository.findAllByOrderBySymbolAsc())
                .thenReturn(List.of(asset("BTC", Asset.AssetType.CRYPTO)));

        service.listAll(null);
        service.listAll(null);

        verify(assetRepository, times(1)).findAllByOrderBySymbolAsc();
    }

    @Test
    void listAllByTypeCachedIndependentlyFromAll() {
        when(assetRepository.findAllByOrderBySymbolAsc())
                .thenReturn(List.of(asset("BTC", Asset.AssetType.CRYPTO)));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.STOCK))
                .thenReturn(List.of(asset("AAPL", Asset.AssetType.STOCK)));

        service.listAll(null);
        service.listAll(Asset.AssetType.STOCK);
        service.listAll(null);
        service.listAll(Asset.AssetType.STOCK);

        verify(assetRepository, times(1)).findAllByOrderBySymbolAsc();
        verify(assetRepository, times(1)).findByAssetTypeOrderBySymbolAsc(Asset.AssetType.STOCK);
    }

    @Test
    void findByIdSecondCallServedFromCache() {
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(id))
                .thenReturn(Optional.of(asset("BTC", Asset.AssetType.CRYPTO)));

        service.findById(id);
        service.findById(id);

        verify(assetRepository, times(1)).findById(id);
    }

    @Test
    void findByIdCachedSeparatelyFromListAll() {
        UUID id = UUID.randomUUID();
        when(assetRepository.findAllByOrderBySymbolAsc())
                .thenReturn(List.of(asset("BTC", Asset.AssetType.CRYPTO)));
        when(assetRepository.findById(id))
                .thenReturn(Optional.of(asset("BTC", Asset.AssetType.CRYPTO)));

        service.listAll(null);
        service.findById(id);

        verify(assetRepository, times(1)).findAllByOrderBySymbolAsc();
        verify(assetRepository, times(1)).findById(id);
    }

    @Test
    void clearingCacheForcesRepoReload() {
        when(assetRepository.findAllByOrderBySymbolAsc())
                .thenReturn(List.of(asset("BTC", Asset.AssetType.CRYPTO)));

        service.listAll(null);
        cacheManager.getCache(CacheConfig.ASSETS_CACHE).clear();
        service.listAll(null);

        verify(assetRepository, times(2)).findAllByOrderBySymbolAsc();
    }

    @Test
    void emptyOptionalIsCached() {
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findById(id)).isEmpty();
        assertThat(service.findById(id)).isEmpty();

        verify(assetRepository, times(1)).findById(id);
    }
}
