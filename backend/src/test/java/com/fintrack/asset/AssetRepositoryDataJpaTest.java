package com.fintrack.asset;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.PostgresDataJpaTest;
import com.fintrack.common.entity.Asset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresDataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class AssetRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired AssetRepository repo;

    private Asset asset(String symbol, Asset.AssetType type) {
        return Asset.builder()
                .symbol(symbol)
                .name(symbol + " Name")
                .assetType(type)
                .currency("TRY")
                .build();
    }

    @Test
    void findAllOrdersBySymbolAsc() {
        repo.save(asset("ETH", Asset.AssetType.CRYPTO));
        repo.save(asset("BTC", Asset.AssetType.CRYPTO));
        repo.save(asset("AAPL", Asset.AssetType.STOCK));

        assertThat(repo.findAllByOrderBySymbolAsc())
                .extracting(Asset::getSymbol)
                .containsExactly("AAPL", "BTC", "ETH");
    }

    @Test
    void findByAssetTypeFilters() {
        repo.save(asset("BTC", Asset.AssetType.CRYPTO));
        repo.save(asset("AAPL", Asset.AssetType.STOCK));

        assertThat(repo.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CRYPTO))
                .extracting(Asset::getSymbol)
                .containsExactly("BTC");
    }

    @Test
    void findBySymbolAndAssetTypeReturnsExactMatch() {
        repo.save(asset("BTC", Asset.AssetType.CRYPTO));
        repo.save(asset("BTC", Asset.AssetType.STOCK));

        assertThat(repo.findBySymbolAndAssetType("BTC", Asset.AssetType.STOCK)).isPresent();
        assertThat(repo.findBySymbolAndAssetType("BTC", Asset.AssetType.GOLD)).isEmpty();
    }
}
