package com.fintrack.portfolio.holding;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.dto.AddHoldingRequest;
import com.fintrack.portfolio.holding.dto.HoldingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Holdings business logic. Every operation verifies portfolio ownership before
 * touching the underlying rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;

    /** Lists holdings for a portfolio owned by the user, joined with asset metadata. */
    @Transactional(readOnly = true)
    public List<HoldingResponse> listForPortfolio(UUID userId, UUID portfolioId) {
        requireOwnedPortfolio(userId, portfolioId);

        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        if (holdings.isEmpty()) {
            return List.of();
        }

        Set<UUID> assetIds = holdings.stream()
                .map(PortfolioHolding::getAssetId)
                .collect(Collectors.toSet());
        Map<UUID, Asset> assetsById = new HashMap<>();
        assetRepository.findAllById(assetIds).forEach(a -> assetsById.put(a.getId(), a));

        return holdings.stream()
                .map(h -> {
                    Asset asset = assetsById.get(h.getAssetId());
                    if (asset == null) {
                        throw new ResourceNotFoundException("Asset not found for holding " + h.getId());
                    }
                    return HoldingResponse.from(h, asset);
                })
                .sorted((a, b) -> a.assetSymbol().compareToIgnoreCase(b.assetSymbol()))
                .toList();
    }

    /** Adds a new holding to the user's portfolio. Fails if the asset is already held. */
    @Transactional
    public HoldingResponse add(UUID userId, UUID portfolioId, AddHoldingRequest request) {
        requireOwnedPortfolio(userId, portfolioId);

        Asset asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        holdingRepository.findByPortfolioIdAndAssetId(portfolioId, request.assetId()).ifPresent(existing -> {
            throw new BusinessRuleException(
                    "This asset is already in the portfolio",
                    "HOLDING_DUPLICATE");
        });

        PortfolioHolding holding = PortfolioHolding.builder()
                .portfolioId(portfolioId)
                .assetId(request.assetId())
                .quantity(request.quantity())
                .avgCostTry(request.avgCostTry())
                .build();

        holding = holdingRepository.save(holding);
        log.info("Holding added: id={} portfolioId={} assetId={} qty={}",
                holding.getId(), portfolioId, request.assetId(), request.quantity());

        return HoldingResponse.from(holding, asset);
    }

    /** Removes a holding from the user's portfolio (hard delete). */
    @Transactional
    public void delete(UUID userId, UUID portfolioId, UUID holdingId) {
        requireOwnedPortfolio(userId, portfolioId);

        PortfolioHolding holding = holdingRepository.findByIdAndPortfolioId(holdingId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding not found"));

        holdingRepository.delete(holding);
        log.info("Holding deleted: id={} portfolioId={}", holdingId, portfolioId);
    }

    private Portfolio requireOwnedPortfolio(UUID userId, UUID portfolioId) {
        return portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }
}
