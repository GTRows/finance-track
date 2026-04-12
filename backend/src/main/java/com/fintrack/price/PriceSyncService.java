package com.fintrack.price;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.price.client.CoinGeckoClient;
import com.fintrack.price.client.ExchangeRateClient;
import com.fintrack.price.client.TefasClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates price refreshes across all configured providers. Each provider
 * failure is isolated: the service always returns a summary of how many assets
 * were updated per source, regardless of individual errors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceSyncService {

    private final AssetRepository assetRepository;
    private final CoinGeckoClient coinGeckoClient;
    private final ExchangeRateClient exchangeRateClient;
    private final TefasClient tefasClient;

    /** Summary row returned to controllers after a refresh. */
    public record SyncResult(
            int cryptoUpdated,
            int currencyUpdated,
            int fundUpdated,
            Instant runAt
    ) {
    }

    /** Runs a full refresh across all providers, including slow fund lookups. */
    @Transactional
    public SyncResult refreshAll() {
        int crypto = refreshCrypto();
        int currency = refreshCurrencies();
        int funds = refreshFunds();
        return new SyncResult(crypto, currency, funds, Instant.now());
    }

    /**
     * Fast refresh: crypto + currency only. Used by the 30 second scheduler
     * since those sources are rate-limit friendly. Fund prices come from TEFAS
     * which only changes once per day and runs on its own hourly schedule.
     */
    @Transactional
    public SyncResult refreshLive() {
        int crypto = refreshCrypto();
        int currency = refreshCurrencies();
        return new SyncResult(crypto, currency, 0, Instant.now());
    }

    /** Updates all CRYPTO assets that carry a {@code coingeckoId} in metadata. */
    @Transactional
    public int refreshCrypto() {
        List<Asset> cryptoAssets = assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CRYPTO);

        Map<String, Asset> byCoingeckoId = new HashMap<>();
        for (Asset a : cryptoAssets) {
            String id = readMetadataString(a, "coingeckoId");
            if (id != null) {
                byCoingeckoId.put(id, a);
            }
        }
        if (byCoingeckoId.isEmpty()) {
            return 0;
        }

        Map<String, CoinGeckoClient.PricePair> prices = coinGeckoClient.fetchPrices(byCoingeckoId.keySet());
        Instant now = Instant.now();
        int updated = 0;
        for (Map.Entry<String, CoinGeckoClient.PricePair> entry : prices.entrySet()) {
            Asset asset = byCoingeckoId.get(entry.getKey());
            if (asset == null) continue;
            CoinGeckoClient.PricePair pair = entry.getValue();
            if (pair.priceTry() != null) {
                asset.setPrice(pair.priceTry());
            }
            if (pair.priceUsd() != null) {
                asset.setPriceUsd(pair.priceUsd());
            }
            asset.setPriceUpdatedAt(now);
            updated++;
        }
        log.info("Crypto price refresh updated {}/{} assets", updated, byCoingeckoId.size());
        return updated;
    }

    /** Updates all CURRENCY assets using exchangerate-api.com. */
    @Transactional
    public int refreshCurrencies() {
        List<Asset> currencyAssets = assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CURRENCY);
        if (currencyAssets.isEmpty()) {
            return 0;
        }

        Set<String> bases = new HashSet<>();
        Map<String, Asset> byBase = new HashMap<>();
        for (Asset a : currencyAssets) {
            String code = readMetadataString(a, "exchangeCode");
            if (code != null) {
                bases.add(code);
                byBase.put(code, a);
            }
        }
        if (bases.isEmpty()) {
            return 0;
        }

        Map<String, BigDecimal> rates = exchangeRateClient.fetchTryRates(bases);
        Instant now = Instant.now();
        int updated = 0;
        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            Asset asset = byBase.get(entry.getKey());
            if (asset == null) continue;
            asset.setPrice(entry.getValue());
            asset.setPriceUpdatedAt(now);
            updated++;
        }
        log.info("Currency price refresh updated {}/{} assets", updated, bases.size());
        return updated;
    }

    /**
     * Updates FUND and GOLD assets that carry a {@code tefasCode} in metadata.
     * TEFAS has no batch endpoint, so this iterates one fund at a time with
     * a small delay between requests to stay friendly to the public server.
     */
    @Transactional
    public int refreshFunds() {
        List<Asset> fundAssets = new ArrayList<>();
        fundAssets.addAll(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.FUND));
        fundAssets.addAll(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.GOLD));

        int total = 0;
        int updated = 0;
        Instant now = Instant.now();

        for (Asset asset : fundAssets) {
            String code = readMetadataString(asset, "tefasCode");
            if (code == null) continue;
            total++;

            String typeCode = readMetadataString(asset, "tefasType");
            TefasClient.FundType type = "EMK".equalsIgnoreCase(typeCode)
                    ? TefasClient.FundType.EMK
                    : TefasClient.FundType.YAT;

            BigDecimal price = tefasClient.fetchPrice(code, type);
            if (price != null && price.signum() > 0) {
                asset.setPrice(price);
                asset.setPriceUpdatedAt(now);
                updated++;
            }

            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (total > 0) {
            log.info("Fund price refresh updated {}/{} assets", updated, total);
        }
        return updated;
    }

    /** Reads a string field from an asset's metadata JSON, or null if missing. */
    private String readMetadataString(Asset asset, String key) {
        if (asset.getMetadata() == null) return null;
        Object value = asset.getMetadata().get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    /** Visible for testing/debugging: list all assets carrying a remote id. */
    public Collection<String> knownCryptoIds() {
        List<String> ids = new ArrayList<>();
        for (Asset a : assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CRYPTO)) {
            String id = readMetadataString(a, "coingeckoId");
            if (id != null) ids.add(id);
        }
        return ids;
    }
}
