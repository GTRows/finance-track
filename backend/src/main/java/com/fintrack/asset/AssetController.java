package com.fintrack.asset;

import com.fintrack.asset.dto.AssetResponse;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PriceHistory;
import com.fintrack.price.PriceHistoryRepository;
import com.fintrack.price.client.CoinGeckoClient;
import com.fintrack.price.client.TefasClient;
import com.fintrack.price.client.YahooFinanceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for browsing the asset master list.
 */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TefasFundService tefasFundService;
    private final TefasClient tefasClient;
    private final CoinGeckoClient coinGeckoClient;
    private final YahooFinanceClient yahooFinanceClient;

    /** Lists all assets, optionally filtered by type. */
    @GetMapping
    public ResponseEntity<List<AssetResponse>> list(
            @RequestParam(value = "type", required = false) Asset.AssetType type) {
        List<Asset> assets = (type == null)
                ? assetRepository.findAllByOrderBySymbolAsc()
                : assetRepository.findByAssetTypeOrderBySymbolAsc(type);
        return ResponseEntity.ok(assets.stream().map(AssetResponse::from).toList());
    }

    /** Returns a single asset by id. */
    @GetMapping("/{assetId}")
    public ResponseEntity<AssetResponse> get(@PathVariable UUID assetId) {
        return assetRepository.findById(assetId)
                .map(AssetResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns price history for an asset over the last N days. Prefers the upstream
     * provider for FUND (TEFAS) and CRYPTO (CoinGecko); otherwise falls back to
     * locally recorded points.
     */
    @GetMapping("/{assetId}/history")
    public ResponseEntity<List<PricePoint>> history(
            @PathVariable UUID assetId,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        int window = Math.max(1, Math.min(days, 365));

        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null) return ResponseEntity.notFound().build();

        List<PricePoint> upstream = fetchUpstreamHistory(asset, window);
        if (!upstream.isEmpty()) {
            return ResponseEntity.ok(upstream);
        }

        Instant since = Instant.now().minus(window, ChronoUnit.DAYS);
        List<PriceHistory> rows = priceHistoryRepository.findSeries(assetId, since);
        return ResponseEntity.ok(rows.stream()
                .map(p -> new PricePoint(p.getRecordedAt(), p.getPrice(), p.getPriceUsd()))
                .toList());
    }

    private List<PricePoint> fetchUpstreamHistory(Asset asset, int days) {
        String id;
        switch (asset.getAssetType()) {
            case FUND, GOLD -> {
                id = metadataString(asset, "tefasCode");
                if (id == null) return List.of();
                String typeCode = metadataString(asset, "tefasType");
                TefasClient.FundType type = "EMK".equalsIgnoreCase(typeCode)
                        ? TefasClient.FundType.EMK
                        : TefasClient.FundType.YAT;
                return tefasClient.fetchHistory(id, type, days).stream()
                        .map(p -> new PricePoint(p.at(), p.price(), null))
                        .toList();
            }
            case CRYPTO -> {
                id = metadataString(asset, "coingeckoId");
                if (id == null) return List.of();
                return coinGeckoClient.fetchHistory(id, days).stream()
                        .map(p -> new PricePoint(p.at(), p.price(), null))
                        .toList();
            }
            case STOCK -> {
                id = metadataString(asset, "yahooSymbol");
                if (id == null) return List.of();
                return yahooFinanceClient.fetchHistory(id, days).stream()
                        .map(p -> new PricePoint(p.at(), p.price(), null))
                        .toList();
            }
            default -> {
                return List.of();
            }
        }
    }

    private String metadataString(Asset asset, String key) {
        if (asset.getMetadata() == null) return null;
        Object value = asset.getMetadata().get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    public record PricePoint(Instant recordedAt, BigDecimal price, BigDecimal priceUsd) {}

    /** Searches TEFAS fund catalog by code or name substring. */
    @GetMapping("/tefas/search")
    public ResponseEntity<List<TefasFundService.FundSearchRow>> searchTefas(
            @RequestParam("q") String query) {
        return ResponseEntity.ok(tefasFundService.search(query));
    }

    /** Imports a TEFAS fund into the asset catalog (or refreshes it if present). */
    @PostMapping("/tefas/import")
    public ResponseEntity<AssetResponse> importTefas(
            @RequestParam("code") String code,
            @RequestParam(value = "type", defaultValue = "YAT") TefasClient.FundType type) {
        Asset asset = tefasFundService.importFund(code, type);
        return ResponseEntity.ok(AssetResponse.from(asset));
    }
}
