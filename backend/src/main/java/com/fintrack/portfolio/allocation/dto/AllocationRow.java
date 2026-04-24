package com.fintrack.portfolio.allocation.dto;

import com.fintrack.common.entity.Asset;
import java.math.BigDecimal;

public record AllocationRow(
        Asset.AssetType assetType,
        BigDecimal targetPercent,
        BigDecimal actualPercent,
        BigDecimal actualValueTry,
        BigDecimal driftPercent,
        BigDecimal driftValueTry) {}
