package com.fintrack.asset;

import com.fintrack.common.entity.Asset;
import com.fintrack.price.PriceSyncService;
import com.fintrack.price.client.TefasClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search and on-demand import of TEFAS funds into the local asset catalog. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TefasFundService {

    private static final int SEARCH_LIMIT = 25;

    private final TefasClient tefasClient;
    private final AssetRepository assetRepository;
    private final PriceSyncService priceSyncService;

    /** Minimal search row returned to the client. */
    public record FundSearchRow(
            String code, String name, TefasClient.FundType type, boolean imported) {}

    /** Searches both YAT and EMK catalogs by code or name substring. */
    public List<FundSearchRow> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) return List.of();

        List<TefasClient.FundSummary> pool = new ArrayList<>();
        pool.addAll(tefasClient.listAll(TefasClient.FundType.YAT));
        pool.addAll(tefasClient.listAll(TefasClient.FundType.EMK));

        Map<String, Asset> existingByCode = existingFundsByTefasCode();

        List<FundSearchRow> result = new ArrayList<>();
        for (TefasClient.FundSummary f : pool) {
            if (result.size() >= SEARCH_LIMIT) break;
            if (f.code().toLowerCase(Locale.ROOT).contains(q)
                    || f.name().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(
                        new FundSearchRow(
                                f.code(),
                                f.name(),
                                f.type(),
                                existingByCode.containsKey(f.code())));
            }
        }
        return result;
    }

    /**
     * Imports a TEFAS fund into the asset catalog (if not already present) and triggers an
     * immediate price fetch. Returns the resulting asset.
     */
    @Transactional
    public Asset importFund(String code, TefasClient.FundType type) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Fund code is required");
        }

        Asset.AssetType assetType = Asset.AssetType.FUND;
        Optional<Asset> existing = assetRepository.findBySymbolAndAssetType(normalized, assetType);
        if (existing.isPresent()) {
            priceSyncService.refreshAsset(existing.get().getId());
            return assetRepository.findById(existing.get().getId()).orElse(existing.get());
        }

        String displayName = resolveName(normalized, type);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tefasCode", normalized);
        metadata.put("tefasType", type.name());
        metadata.put("source", "tefas-import");

        Asset asset =
                Asset.builder()
                        .symbol(normalized)
                        .name(displayName)
                        .assetType(assetType)
                        .currency("TRY")
                        .metadata(metadata)
                        .build();

        Asset saved = assetRepository.save(asset);
        log.info("Imported TEFAS fund code={} type={} assetId={}", normalized, type, saved.getId());

        priceSyncService.refreshAsset(saved.getId());
        return assetRepository.findById(saved.getId()).orElse(saved);
    }

    private String resolveName(String code, TefasClient.FundType type) {
        for (TefasClient.FundSummary f : tefasClient.listAll(type)) {
            if (f.code().equalsIgnoreCase(code)) return f.name();
        }
        TefasClient.FundType other =
                type == TefasClient.FundType.YAT
                        ? TefasClient.FundType.EMK
                        : TefasClient.FundType.YAT;
        for (TefasClient.FundSummary f : tefasClient.listAll(other)) {
            if (f.code().equalsIgnoreCase(code)) return f.name();
        }
        return code;
    }

    private Map<String, Asset> existingFundsByTefasCode() {
        Map<String, Asset> result = new HashMap<>();
        for (Asset a : assetRepository.findByAssetTypeOrderBySymbolAsc(Asset.AssetType.FUND)) {
            Object code = a.getMetadata() == null ? null : a.getMetadata().get("tefasCode");
            if (code instanceof String s && !s.isBlank()) {
                result.put(s, a);
            }
        }
        return result;
    }
}
