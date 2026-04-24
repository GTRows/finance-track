package com.fintrack.asset;

import com.fintrack.common.entity.Asset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA repository for {@link Asset} master data. */
@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    /** Returns all assets ordered by symbol. */
    List<Asset> findAllByOrderBySymbolAsc();

    /** Filters assets by type. */
    List<Asset> findByAssetTypeOrderBySymbolAsc(Asset.AssetType type);

    /** Lookup an asset by symbol within a type (unique pair). */
    Optional<Asset> findBySymbolAndAssetType(String symbol, Asset.AssetType assetType);
}
