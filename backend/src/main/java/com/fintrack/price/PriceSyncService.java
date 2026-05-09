package com.fintrack.price;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PriceHistory;
import com.fintrack.metrics.PriceSyncMetrics;
import com.fintrack.price.client.CoinGeckoClient;
import com.fintrack.price.client.ExchangeRateClient;
import com.fintrack.price.client.PreciousMetalsClient;
import com.fintrack.price.client.TefasClient;
import com.fintrack.price.client.YahooFinanceClient;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates price refreshes across all configured providers. Each provider failure is isolated:
 * the service always returns a summary of how many assets were updated per source, regardless of
 * individual errors.
 *
 * <p>External HTTP calls fan out on a named virtual-thread executor ({@code priceVirtualExecutor})
 * so {@code WebClient.block()} inside each client does not pin a platform thread. Writes are
 * collected into {@link PriceUpdate} records and committed in a single short {@code @Transactional}
 * window via {@link #persistUpdates(List)} — the per-source public methods are deliberately not
 * {@code @Transactional}.
 */
@Service
@Slf4j
public class PriceSyncService {

    private static final BigDecimal GRAMS_PER_OUNCE = new BigDecimal("31.1034768");

    private final AssetRepository assetRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final CoinGeckoClient coinGeckoClient;
    private final ExchangeRateClient exchangeRateClient;
    private final TefasClient tefasClient;
    private final PreciousMetalsClient preciousMetalsClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final ExecutorService priceVirtualExecutor;
    private final PriceApiProperties priceApiProperties;
    private final @Nullable PriceSyncMetrics priceSyncMetrics;

    public PriceSyncService(
            AssetRepository assetRepository,
            PriceHistoryRepository priceHistoryRepository,
            CoinGeckoClient coinGeckoClient,
            ExchangeRateClient exchangeRateClient,
            TefasClient tefasClient,
            PreciousMetalsClient preciousMetalsClient,
            YahooFinanceClient yahooFinanceClient,
            @Qualifier("tracingPriceVirtualExecutor") ExecutorService priceVirtualExecutor,
            PriceApiProperties priceApiProperties,
            @Nullable PriceSyncMetrics priceSyncMetrics) {
        this.assetRepository = assetRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.coinGeckoClient = coinGeckoClient;
        this.exchangeRateClient = exchangeRateClient;
        this.tefasClient = tefasClient;
        this.preciousMetalsClient = preciousMetalsClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.priceVirtualExecutor = priceVirtualExecutor;
        this.priceApiProperties = priceApiProperties;
        this.priceSyncMetrics = priceSyncMetrics;
    }

    /**
     * Records a successful refresh on the freshness gauge for {@code source} when at least one
     * update flowed through and the metrics bean is wired. Test fixtures that pass {@code null} for
     * {@link PriceSyncMetrics} short-circuit here.
     */
    private void recordSuccessIfPresent(PriceSyncMetrics.Source source, int updatedCount) {
        if (priceSyncMetrics == null) return;
        if (updatedCount <= 0) return;
        priceSyncMetrics.recordSuccess(source, Instant.now());
    }

    /** Summary row returned to controllers after a refresh. */
    public record SyncResult(
            int cryptoUpdated,
            int currencyUpdated,
            int fundUpdated,
            int metalUpdated,
            int stockUpdated,
            Instant runAt) {}

    /**
     * Pipeline payload moved between the per-source {@code fetch*()} stages and the single
     * transactional {@link #persistUpdates(List)} commit point. The asset reference is the one read
     * by the fetch stage; {@link #persistUpdates(List)} mutates and saves it back. {@code usdPrice}
     * is optional.
     */
    private record PriceUpdate(Asset asset, BigDecimal tryPrice, BigDecimal usdPrice) {}

    /** Bounded fund descriptor used to fan out per-fund TEFAS reads off-transaction. */
    private record FundLookup(Asset asset, String code, TefasClient.FundType type) {}

    /** Runs a full refresh across all providers, including slow fund lookups. */
    public SyncResult refreshAll() {
        int crypto = refreshCrypto();
        int currency = refreshCurrencies();
        int funds = refreshFunds();
        int metals = refreshMetals();
        int stocks = refreshStocks();
        return new SyncResult(crypto, currency, funds, metals, stocks, Instant.now());
    }

    /**
     * Fast refresh: crypto + currency + metals + stocks. Used by the 30 second scheduler since
     * those sources are rate-limit friendly. Fund prices come from TEFAS which only changes once
     * per day and runs on its own schedule. The four upstream HTTP fan-outs run in parallel on
     * {@code priceVirtualExecutor}; persistence happens once at the end.
     */
    @Observed(name = "price.refresh.live", contextualName = "price.refresh.live")
    public SyncResult refreshLive() {
        CompletableFuture<List<PriceUpdate>> cryptoF =
                CompletableFuture.supplyAsync(this::fetchCrypto, priceVirtualExecutor);
        CompletableFuture<List<PriceUpdate>> currencyF =
                CompletableFuture.supplyAsync(this::fetchCurrencies, priceVirtualExecutor);
        CompletableFuture<List<PriceUpdate>> metalsF =
                CompletableFuture.supplyAsync(this::fetchMetals, priceVirtualExecutor);
        CompletableFuture<List<PriceUpdate>> stocksF =
                CompletableFuture.supplyAsync(this::fetchStocks, priceVirtualExecutor);

        List<PriceUpdate> crypto = cryptoF.join();
        List<PriceUpdate> currency = currencyF.join();
        List<PriceUpdate> metals = metalsF.join();
        List<PriceUpdate> stocks = stocksF.join();

        // Record per-source freshness BEFORE persistence -- the fetch was successful regardless
        // of any downstream persistence failure.
        recordSuccessIfPresent(PriceSyncMetrics.Source.CRYPTO, crypto.size());
        recordSuccessIfPresent(PriceSyncMetrics.Source.CURRENCY, currency.size());
        recordSuccessIfPresent(PriceSyncMetrics.Source.METAL, metals.size());
        recordSuccessIfPresent(PriceSyncMetrics.Source.STOCK, stocks.size());

        List<PriceUpdate> all = new ArrayList<>();
        all.addAll(crypto);
        all.addAll(currency);
        all.addAll(metals);
        all.addAll(stocks);
        persistUpdates(all);

        return new SyncResult(
                crypto.size(), currency.size(), 0, metals.size(), stocks.size(), Instant.now());
    }

    /** Updates all CRYPTO assets that carry a {@code coingeckoId} in metadata. */
    public int refreshCrypto() {
        int written = persistUpdates(fetchCrypto());
        recordSuccessIfPresent(PriceSyncMetrics.Source.CRYPTO, written);
        return written;
    }

    /** Updates all CURRENCY assets using exchangerate-api.com. */
    public int refreshCurrencies() {
        int written = persistUpdates(fetchCurrencies());
        recordSuccessIfPresent(PriceSyncMetrics.Source.CURRENCY, written);
        return written;
    }

    /**
     * Updates FUND and GOLD assets that carry a {@code tefasCode} in metadata. TEFAS has no batch
     * endpoint, so the per-fund reads fan out on {@code priceVirtualExecutor} with a {@link
     * Semaphore} permit (size = {@code price-api.tefas.parallelism}, default 4) to cap upstream
     * concurrency. GOLD assets carrying a {@code metalsSymbol} are skipped here and handled by
     * {@link #refreshMetals()} instead.
     */
    @Observed(name = "price.refresh.funds", contextualName = "price.refresh.funds")
    public int refreshFunds() {
        List<PriceUpdate> updates = fetchFunds();
        int total = countFundTargets();
        int updated = persistUpdates(updates);
        if (total > 0) {
            log.info("Fund price refresh updated {}/{} assets", updated, total);
        }
        return updated;
    }

    /**
     * Updates GOLD assets that carry a {@code metalsSymbol} (XAU/XAG/XPT/XPD). Fetches
     * USD-per-ounce from the metals client, multiplies by the current USD/TRY rate, and optionally
     * scales to a gram price when the asset has {@code metalsUnit=gram} in its metadata.
     */
    public int refreshMetals() {
        int written = persistUpdates(fetchMetals());
        recordSuccessIfPresent(PriceSyncMetrics.Source.METAL, written);
        return written;
    }

    /**
     * Updates STOCK assets that carry a {@code yahooSymbol} in metadata (BIST tickers use the
     * {@code .IS} suffix, e.g. {@code THYAO.IS}). Prices are returned in the asset's native
     * currency; USD quotes are converted to TRY using the live FX rate and also recorded on {@code
     * priceUsd}.
     */
    public int refreshStocks() {
        int written = persistUpdates(fetchStocks());
        recordSuccessIfPresent(PriceSyncMetrics.Source.STOCK, written);
        return written;
    }

    // ---------------------------------------------------------------------------------------------
    // Fetch stages — pure HTTP, no transaction. Returned PriceUpdate rows are merged and committed
    // by persistUpdates(...) in a single short transactional window.
    // ---------------------------------------------------------------------------------------------

    private List<PriceUpdate> fetchCrypto() {
        List<Asset> cryptoAssets =
                assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CRYPTO);

        Map<String, Asset> byCoingeckoId = new HashMap<>();
        for (Asset a : cryptoAssets) {
            String id = readMetadataString(a, "coingeckoId");
            if (id != null) {
                byCoingeckoId.put(id, a);
            }
        }
        if (byCoingeckoId.isEmpty()) {
            return List.of();
        }

        Map<String, CoinGeckoClient.PricePair> prices =
                coinGeckoClient.fetchPrices(byCoingeckoId.keySet());
        List<PriceUpdate> updates = new ArrayList<>();
        for (Map.Entry<String, CoinGeckoClient.PricePair> entry : prices.entrySet()) {
            Asset asset = byCoingeckoId.get(entry.getKey());
            if (asset == null) continue;
            CoinGeckoClient.PricePair pair = entry.getValue();
            BigDecimal tryPrice = pair.priceTry();
            BigDecimal usdPrice = pair.priceUsd();
            if (tryPrice == null && usdPrice == null) continue;
            updates.add(new PriceUpdate(asset, tryPrice, usdPrice));
        }
        return updates;
    }

    private List<PriceUpdate> fetchCurrencies() {
        List<Asset> currencyAssets =
                assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.CURRENCY);
        if (currencyAssets.isEmpty()) {
            return List.of();
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
            return List.of();
        }

        Map<String, BigDecimal> rates = exchangeRateClient.fetchTryRates(bases);
        List<PriceUpdate> updates = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            Asset asset = byBase.get(entry.getKey());
            if (asset == null) continue;
            updates.add(new PriceUpdate(asset, entry.getValue(), null));
        }
        return updates;
    }

    private List<PriceUpdate> fetchMetals() {
        List<Asset> goldAssets =
                assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.GOLD);
        List<Asset> metalAssets = new ArrayList<>();
        Set<String> symbols = new HashSet<>();
        for (Asset a : goldAssets) {
            String sym = readMetadataString(a, "metalsSymbol");
            if (sym == null) continue;
            metalAssets.add(a);
            symbols.add(sym.toUpperCase());
        }
        if (metalAssets.isEmpty()) return List.of();

        Map<String, BigDecimal> usdPerOunce = preciousMetalsClient.fetchUsdPerOunce(symbols);
        if (usdPerOunce.isEmpty()) {
            log.warn("Metal price refresh: no prices returned for symbols {}", symbols);
            return List.of();
        }

        Map<String, BigDecimal> fx = exchangeRateClient.fetchTryRates(Set.of("USD"));
        BigDecimal usdTry = fx.get("USD");
        if (usdTry == null || usdTry.signum() <= 0) {
            log.warn("Metal price refresh skipped: USD/TRY rate unavailable");
            return List.of();
        }

        List<PriceUpdate> updates = new ArrayList<>();
        for (Asset asset : metalAssets) {
            String sym = readMetadataString(asset, "metalsSymbol").toUpperCase();
            BigDecimal usd = usdPerOunce.get(sym);
            if (usd == null || usd.signum() <= 0) continue;

            String unit = readMetadataString(asset, "metalsUnit");
            BigDecimal usdForUnit =
                    "gram".equalsIgnoreCase(unit)
                            ? usd.divide(GRAMS_PER_OUNCE, 6, RoundingMode.HALF_UP)
                            : usd;
            BigDecimal tryPrice = usdForUnit.multiply(usdTry).setScale(4, RoundingMode.HALF_UP);
            BigDecimal usdScaled = usdForUnit.setScale(4, RoundingMode.HALF_UP);
            updates.add(new PriceUpdate(asset, tryPrice, usdScaled));
        }
        return updates;
    }

    private List<PriceUpdate> fetchStocks() {
        List<Asset> stockAssets =
                assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.STOCK);
        Map<String, Asset> bySymbol = new HashMap<>();
        for (Asset a : stockAssets) {
            String sym = readMetadataString(a, "yahooSymbol");
            if (sym != null) bySymbol.put(sym, a);
        }
        if (bySymbol.isEmpty()) return List.of();

        Map<String, YahooFinanceClient.Quote> quotes =
                yahooFinanceClient.fetchQuotes(bySymbol.keySet());
        if (quotes.isEmpty()) return List.of();

        BigDecimal usdTry = null;
        boolean needsFx =
                quotes.values().stream()
                        .anyMatch(q -> q != null && "USD".equalsIgnoreCase(q.currency()));
        if (needsFx) {
            Map<String, BigDecimal> fx = exchangeRateClient.fetchTryRates(Set.of("USD"));
            usdTry = fx.get("USD");
        }

        List<PriceUpdate> updates = new ArrayList<>();
        for (Map.Entry<String, YahooFinanceClient.Quote> entry : quotes.entrySet()) {
            Asset asset = bySymbol.get(entry.getKey());
            YahooFinanceClient.Quote quote = entry.getValue();
            if (asset == null || quote == null || quote.price() == null) continue;

            String currency = quote.currency() == null ? "" : quote.currency().toUpperCase();
            BigDecimal tryPrice;
            BigDecimal usdPrice = null;
            if ("TRY".equals(currency) || currency.isBlank()) {
                tryPrice = quote.price();
            } else if ("USD".equals(currency)) {
                if (usdTry == null || usdTry.signum() <= 0) continue;
                usdPrice = quote.price();
                tryPrice = quote.price().multiply(usdTry).setScale(4, RoundingMode.HALF_UP);
            } else {
                log.debug("Stock {}: unsupported currency {}, skipping", entry.getKey(), currency);
                continue;
            }
            updates.add(new PriceUpdate(asset, tryPrice, usdPrice));
        }
        return updates;
    }

    private List<PriceUpdate> fetchFunds() {
        List<FundLookup> targets = readFundTargets();
        if (targets.isEmpty()) return List.of();

        int parallelism = Math.max(1, priceApiProperties.tefas().parallelism());
        Semaphore gate = new Semaphore(parallelism);

        List<CompletableFuture<PriceUpdate>> futures = new ArrayList<>(targets.size());
        for (FundLookup target : targets) {
            futures.add(
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    gate.acquire();
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    return null;
                                }
                                try {
                                    BigDecimal price =
                                            tefasClient.fetchPrice(target.code(), target.type());
                                    if (price == null || price.signum() <= 0) return null;
                                    return new PriceUpdate(target.asset(), price, null);
                                } finally {
                                    gate.release();
                                }
                            },
                            priceVirtualExecutor));
        }

        return futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    protected List<FundLookup> readFundTargets() {
        List<Asset> fundAssets = new ArrayList<>();
        fundAssets.addAll(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.FUND));
        fundAssets.addAll(assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.GOLD));

        List<FundLookup> targets = new ArrayList<>();
        for (Asset asset : fundAssets) {
            if (readMetadataString(asset, "metalsSymbol") != null) continue;
            String code = readMetadataString(asset, "tefasCode");
            if (code == null) continue;
            String typeCode = readMetadataString(asset, "tefasType");
            TefasClient.FundType type =
                    "EMK".equalsIgnoreCase(typeCode)
                            ? TefasClient.FundType.EMK
                            : TefasClient.FundType.YAT;
            targets.add(new FundLookup(asset, code, type));
        }
        return targets;
    }

    private int countFundTargets() {
        return readFundTargets().size();
    }

    /**
     * Single transactional commit point for every per-source pipeline. Applies the new prices to
     * each (detached) asset captured during the fetch stage and writes a {@link PriceHistory} row
     * per update. Returns the number of updates that took effect.
     *
     * <p>Evicts the entire {@code analytics:correlations} cache because the matrix derives from
     * {@code price_history} and any new row may shift any cell. Per-asset eviction is impractical
     * since callers select arbitrary asset combinations.
     */
    @Transactional
    @CacheEvict(
            value = {
                CacheConfig.ANALYTICS_CORRELATIONS_CACHE,
                CacheConfig.ANALYTICS_MONTE_CARLO_CACHE
            },
            allEntries = true)
    protected int persistUpdates(List<PriceUpdate> updates) {
        if (updates == null || updates.isEmpty()) return 0;

        Instant now = Instant.now();
        int written = 0;
        for (PriceUpdate u : updates) {
            if (u == null || u.asset() == null) continue;
            Asset asset = u.asset();
            if (u.tryPrice() != null) asset.setPrice(u.tryPrice());
            if (u.usdPrice() != null) asset.setPriceUsd(u.usdPrice());
            asset.setPriceUpdatedAt(now);
            assetRepository.save(asset);
            recordHistory(asset, now);
            written++;
        }
        return written;
    }

    /** Refreshes a single asset by id and returns true if a new price was written. */
    @Transactional
    @CacheEvict(
            value = {
                CacheConfig.ANALYTICS_CORRELATIONS_CACHE,
                CacheConfig.ANALYTICS_MONTE_CARLO_CACHE
            },
            allEntries = true)
    public boolean refreshAsset(UUID assetId) {
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null) return false;

        Instant now = Instant.now();
        switch (asset.getAssetType()) {
            case CRYPTO -> {
                String id = readMetadataString(asset, "coingeckoId");
                if (id == null) return false;
                Map<String, CoinGeckoClient.PricePair> prices =
                        coinGeckoClient.fetchPrices(List.of(id));
                CoinGeckoClient.PricePair pair = prices.get(id);
                if (pair == null) return false;
                if (pair.priceTry() != null) asset.setPrice(pair.priceTry());
                if (pair.priceUsd() != null) asset.setPriceUsd(pair.priceUsd());
                asset.setPriceUpdatedAt(now);
                recordHistory(asset, now);
                return true;
            }
            case CURRENCY -> {
                String code = readMetadataString(asset, "exchangeCode");
                if (code == null) return false;
                Map<String, BigDecimal> rates = exchangeRateClient.fetchTryRates(Set.of(code));
                BigDecimal rate = rates.get(code);
                if (rate == null) return false;
                asset.setPrice(rate);
                asset.setPriceUpdatedAt(now);
                recordHistory(asset, now);
                return true;
            }
            case FUND, GOLD -> {
                String metalsSymbol = readMetadataString(asset, "metalsSymbol");
                if (metalsSymbol != null) {
                    Map<String, BigDecimal> usdPerOunce =
                            preciousMetalsClient.fetchUsdPerOunce(List.of(metalsSymbol));
                    BigDecimal usd = usdPerOunce.get(metalsSymbol.toUpperCase());
                    if (usd == null || usd.signum() <= 0) return false;

                    Map<String, BigDecimal> fx = exchangeRateClient.fetchTryRates(Set.of("USD"));
                    BigDecimal usdTry = fx.get("USD");
                    if (usdTry == null || usdTry.signum() <= 0) return false;

                    String unit = readMetadataString(asset, "metalsUnit");
                    BigDecimal usdForUnit =
                            "gram".equalsIgnoreCase(unit)
                                    ? usd.divide(GRAMS_PER_OUNCE, 6, RoundingMode.HALF_UP)
                                    : usd;
                    asset.setPrice(usdForUnit.multiply(usdTry).setScale(4, RoundingMode.HALF_UP));
                    asset.setPriceUsd(usdForUnit.setScale(4, RoundingMode.HALF_UP));
                    asset.setPriceUpdatedAt(now);
                    recordHistory(asset, now);
                    return true;
                }
                String code = readMetadataString(asset, "tefasCode");
                if (code == null) return false;
                String typeCode = readMetadataString(asset, "tefasType");
                TefasClient.FundType type =
                        "EMK".equalsIgnoreCase(typeCode)
                                ? TefasClient.FundType.EMK
                                : TefasClient.FundType.YAT;
                BigDecimal price = tefasClient.fetchPrice(code, type);
                if (price == null || price.signum() <= 0) return false;
                asset.setPrice(price);
                asset.setPriceUpdatedAt(now);
                recordHistory(asset, now);
                return true;
            }
            case STOCK -> {
                String sym = readMetadataString(asset, "yahooSymbol");
                if (sym == null) return false;
                Map<String, YahooFinanceClient.Quote> quotes =
                        yahooFinanceClient.fetchQuotes(List.of(sym));
                YahooFinanceClient.Quote quote = quotes.get(sym);
                if (quote == null || quote.price() == null) return false;

                String currency = quote.currency() == null ? "" : quote.currency().toUpperCase();
                BigDecimal tryPrice;
                BigDecimal usdPrice = null;
                if ("TRY".equals(currency) || currency.isBlank()) {
                    tryPrice = quote.price();
                } else if ("USD".equals(currency)) {
                    Map<String, BigDecimal> fx = exchangeRateClient.fetchTryRates(Set.of("USD"));
                    BigDecimal usdTry = fx.get("USD");
                    if (usdTry == null || usdTry.signum() <= 0) return false;
                    usdPrice = quote.price();
                    tryPrice = quote.price().multiply(usdTry).setScale(4, RoundingMode.HALF_UP);
                } else {
                    return false;
                }
                asset.setPrice(tryPrice);
                if (usdPrice != null) asset.setPriceUsd(usdPrice);
                asset.setPriceUpdatedAt(now);
                recordHistory(asset, now);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Appends a history row capturing the current price of an asset. */
    private void recordHistory(Asset asset, Instant at) {
        if (asset.getPrice() == null) return;
        priceHistoryRepository.save(
                PriceHistory.builder()
                        .assetId(asset.getId())
                        .price(asset.getPrice())
                        .priceUsd(asset.getPriceUsd())
                        .recordedAt(at)
                        .build());
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
