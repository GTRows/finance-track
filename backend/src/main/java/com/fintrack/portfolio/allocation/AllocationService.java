package com.fintrack.portfolio.allocation;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.PortfolioAllocationTarget;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.allocation.dto.AllocationRow;
import com.fintrack.portfolio.allocation.dto.AllocationSummary;
import com.fintrack.portfolio.allocation.dto.AllocationTargetInput;
import com.fintrack.portfolio.allocation.dto.SetAllocationRequest;
import com.fintrack.portfolio.holding.HoldingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AllocationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.05");

    private final AllocationTargetRepository targetRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final AssetRepository assetRepository;

    @Transactional(readOnly = true)
    public AllocationSummary summarize(UUID userId, UUID portfolioId) {
        requireOwnership(userId, portfolioId);

        List<PortfolioAllocationTarget> targets = targetRepository.findByPortfolioId(portfolioId);
        Map<Asset.AssetType, BigDecimal> targetByType = new EnumMap<>(Asset.AssetType.class);
        for (PortfolioAllocationTarget t : targets) {
            targetByType.put(t.getAssetType(), t.getTargetPercent());
        }

        Map<Asset.AssetType, BigDecimal> actualValueByType = actualValueByType(portfolioId);
        BigDecimal totalValue =
                actualValueByType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<Asset.AssetType> types = new TreeSet<>();
        types.addAll(targetByType.keySet());
        types.addAll(actualValueByType.keySet());

        List<AllocationRow> rows =
                types.stream()
                        .map(
                                type ->
                                        buildRow(
                                                type,
                                                targetByType.get(type),
                                                actualValueByType.get(type),
                                                totalValue))
                        .toList();

        return new AllocationSummary(totalValue, !targets.isEmpty(), rows);
    }

    @Transactional
    public AllocationSummary replaceTargets(
            UUID userId, UUID portfolioId, SetAllocationRequest request) {
        requireOwnership(userId, portfolioId);

        if (!request.targets().isEmpty()) {
            validateTargets(request.targets());
        }

        targetRepository.deleteByPortfolioId(portfolioId);
        for (AllocationTargetInput input : request.targets()) {
            if (input.targetPercent().signum() == 0) continue;
            targetRepository.save(
                    PortfolioAllocationTarget.builder()
                            .portfolioId(portfolioId)
                            .assetType(input.assetType())
                            .targetPercent(input.targetPercent())
                            .build());
        }
        log.info(
                "Allocation targets updated: portfolioId={} count={}",
                portfolioId,
                request.targets().size());
        return summarize(userId, portfolioId);
    }

    private void validateTargets(List<AllocationTargetInput> inputs) {
        Set<Asset.AssetType> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (AllocationTargetInput t : inputs) {
            if (!seen.add(t.assetType())) {
                throw new BusinessRuleException(
                        "Duplicate allocation target: " + t.assetType(), "ALLOCATION_DUPLICATE");
            }
            sum = sum.add(t.targetPercent());
        }
        if (sum.subtract(HUNDRED).abs().compareTo(SUM_TOLERANCE) > 0) {
            throw new BusinessRuleException(
                    "Allocation targets must sum to 100 (got "
                            + sum.stripTrailingZeros().toPlainString()
                            + ")",
                    "ALLOCATION_SUM");
        }
    }

    private Map<Asset.AssetType, BigDecimal> actualValueByType(UUID portfolioId) {
        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        if (holdings.isEmpty()) return new EnumMap<>(Asset.AssetType.class);

        Set<UUID> assetIds = new HashSet<>();
        for (PortfolioHolding h : holdings) assetIds.add(h.getAssetId());
        Map<UUID, Asset> byId = new HashMap<>();
        assetRepository.findAllById(assetIds).forEach(a -> byId.put(a.getId(), a));

        Map<Asset.AssetType, BigDecimal> totals = new EnumMap<>(Asset.AssetType.class);
        for (PortfolioHolding h : holdings) {
            Asset asset = byId.get(h.getAssetId());
            if (asset == null || asset.getPrice() == null || h.getQuantity() == null) continue;
            BigDecimal value = asset.getPrice().multiply(h.getQuantity());
            totals.merge(asset.getAssetType(), value, BigDecimal::add);
        }
        return totals;
    }

    private AllocationRow buildRow(
            Asset.AssetType type,
            BigDecimal targetPercent,
            BigDecimal actualValue,
            BigDecimal totalValue) {
        BigDecimal target = targetPercent != null ? targetPercent : BigDecimal.ZERO;
        BigDecimal value = actualValue != null ? actualValue : BigDecimal.ZERO;
        BigDecimal actualPercent =
                totalValue.signum() > 0
                        ? value.multiply(HUNDRED).divide(totalValue, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        BigDecimal drift = actualPercent.subtract(target);
        BigDecimal driftValue = totalValue.multiply(drift).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new AllocationRow(
                type,
                target.setScale(2, RoundingMode.HALF_UP),
                actualPercent,
                value.setScale(2, RoundingMode.HALF_UP),
                drift.setScale(2, RoundingMode.HALF_UP),
                driftValue);
    }

    private void requireOwnership(UUID userId, UUID portfolioId) {
        portfolioRepository
                .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }
}
