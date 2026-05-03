package com.fintrack.price;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PriceHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class PriceHistoryRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired PriceHistoryRepository repo;
    @Autowired AssetRepository assetRepo;

    private UUID seedAsset(String symbol) {
        return assetRepo.save(
                        Asset.builder()
                                .symbol(symbol)
                                .name(symbol)
                                .assetType(Asset.AssetType.CRYPTO)
                                .currency("TRY")
                                .build())
                .getId();
    }

    private PriceHistory price(UUID assetId, Instant when, String value) {
        return PriceHistory.builder()
                .assetId(assetId)
                .price(new BigDecimal(value))
                .recordedAt(when)
                .build();
    }

    @Test
    void findSeriesReturnsAscendingFromCutoff() {
        UUID asset = seedAsset("BTC");
        Instant base = Instant.parse("2026-04-01T00:00:00Z");
        repo.save(price(asset, base, "100"));
        repo.save(price(asset, base.plus(2, ChronoUnit.HOURS), "120"));
        repo.save(price(asset, base.plus(1, ChronoUnit.HOURS), "110"));

        var series = repo.findSeries(asset, base);

        assertThat(series)
                .extracting(PriceHistory::getPrice)
                .extracting(BigDecimal::intValue)
                .containsExactly(100, 110, 120);
    }

    @Test
    void findSeriesExcludesBeforeCutoff() {
        UUID asset = seedAsset("ETH");
        Instant base = Instant.parse("2026-04-01T00:00:00Z");
        repo.save(price(asset, base.minus(1, ChronoUnit.HOURS), "50"));
        repo.save(price(asset, base.plus(1, ChronoUnit.HOURS), "60"));

        var series = repo.findSeries(asset, base);

        assertThat(series).hasSize(1);
        assertThat(series.get(0).getPrice()).isEqualByComparingTo("60");
    }

    @Test
    void findSeriesEmptyForUnknownAsset() {
        assertThat(repo.findSeries(UUID.randomUUID(), Instant.now())).isEmpty();
    }
}
