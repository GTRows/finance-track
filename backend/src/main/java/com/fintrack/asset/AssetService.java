package com.fintrack.asset;

import com.fintrack.asset.dto.AssetResponse;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.Asset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cache-aware read facade over {@link AssetRepository}. The two methods are reached through a
 * Spring proxy so {@code @Cacheable} can intercept; controllers call this service instead of the
 * repository for the asset list / by-id reads. The underlying entity-touching paths (price history
 * fetch, TEFAS metadata lookup) keep direct repository access in their callers.
 */
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;

    /**
     * Returns all assets, optionally filtered by type. Cached under the type name (or {@code 'ALL'}
     * when no filter) so repeated dashboard loads inside the TTL window do not hit the DB.
     */
    @Cacheable(value = CacheConfig.ASSETS_CACHE, key = "#type != null ? #type.name() : 'ALL'")
    @Transactional(readOnly = true)
    public List<AssetResponse> listAll(Asset.AssetType type) {
        List<Asset> assets =
                (type == null)
                        ? assetRepository.findAllByOrderBySymbolAsc()
                        : assetRepository.findByAssetTypeOrderBySymbolAsc(type);
        return assets.stream().map(AssetResponse::from).toList();
    }

    /**
     * Returns a single asset by id. The {@code 'ID:'} prefix on the cache key avoids collision with
     * the type-keyed entries from {@link #listAll(Asset.AssetType)}.
     */
    @Cacheable(value = CacheConfig.ASSETS_CACHE, key = "'ID:' + #assetId")
    @Transactional(readOnly = true)
    public Optional<AssetResponse> findById(UUID assetId) {
        return assetRepository.findById(assetId).map(AssetResponse::from);
    }
}
