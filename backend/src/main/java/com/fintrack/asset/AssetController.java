package com.fintrack.asset;

import com.fintrack.asset.dto.AssetResponse;
import com.fintrack.common.entity.Asset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for browsing the asset master list.
 */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;

    /** Lists all assets, optionally filtered by type. */
    @GetMapping
    public ResponseEntity<List<AssetResponse>> list(
            @RequestParam(value = "type", required = false) Asset.AssetType type) {
        List<Asset> assets = (type == null)
                ? assetRepository.findAllByOrderBySymbolAsc()
                : assetRepository.findByAssetTypeOrderBySymbolAsc(type);
        return ResponseEntity.ok(assets.stream().map(AssetResponse::from).toList());
    }
}
