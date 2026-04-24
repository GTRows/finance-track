package com.fintrack.portfolio.snapshot;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.snapshot.dto.SnapshotResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures and reads daily portfolio snapshots. Snapshots power the historical value chart and
 * future analytics such as XIRR / drawdown.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SnapshotService {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final SnapshotRepository snapshotRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final AssetRepository assetRepository;

    /**
     * Captures one snapshot per active portfolio for today (Europe/Istanbul). Safe to run multiple
     * times per day -- existing rows are updated in place.
     */
    @Transactional
    public CaptureResult captureDaily() {
        LocalDate today = LocalDate.now(ISTANBUL);
        List<Portfolio> portfolios = portfolioRepository.findAllByActiveTrue();
        if (portfolios.isEmpty()) {
            return new CaptureResult(today, 0, 0);
        }

        int created = 0;
        int updated = 0;

        for (Portfolio portfolio : portfolios) {
            ValuedPortfolio valued = valuate(portfolio.getId());

            PortfolioSnapshot snapshot =
                    snapshotRepository
                            .findByPortfolioIdAndSnapshotDate(portfolio.getId(), today)
                            .orElse(null);

            if (snapshot == null) {
                snapshot =
                        PortfolioSnapshot.builder()
                                .portfolioId(portfolio.getId())
                                .snapshotDate(today)
                                .totalValueTry(valued.totalValue())
                                .totalCostTry(valued.totalCost())
                                .holdingsJson(valued.holdingsJson())
                                .build();
                snapshotRepository.save(snapshot);
                created++;
            } else {
                snapshot.setTotalValueTry(valued.totalValue());
                snapshot.setTotalCostTry(valued.totalCost());
                snapshot.setHoldingsJson(valued.holdingsJson());
                updated++;
            }
        }

        log.info(
                "Daily snapshots captured: date={} created={} updated={}", today, created, updated);
        return new CaptureResult(today, created, updated);
    }

    /** Returns the full chronological history for a portfolio owned by the user. */
    @Transactional(readOnly = true)
    public List<SnapshotResponse> listForPortfolio(UUID userId, UUID portfolioId) {
        portfolioRepository
                .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));

        return snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId).stream()
                .map(SnapshotResponse::from)
                .toList();
    }

    /** Computes the current value and cost basis for a portfolio from live asset prices. */
    private ValuedPortfolio valuate(UUID portfolioId) {
        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        if (holdings.isEmpty()) {
            return new ValuedPortfolio(BigDecimal.ZERO, BigDecimal.ZERO, new LinkedHashMap<>());
        }

        Set<UUID> assetIds =
                holdings.stream().map(PortfolioHolding::getAssetId).collect(Collectors.toSet());
        Map<UUID, Asset> assetsById = new HashMap<>();
        assetRepository.findAllById(assetIds).forEach(a -> assetsById.put(a.getId(), a));

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        Map<String, Object> holdingsJson = new LinkedHashMap<>();

        for (PortfolioHolding holding : holdings) {
            Asset asset = assetsById.get(holding.getAssetId());
            if (asset == null) {
                continue;
            }
            BigDecimal quantity =
                    holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
            BigDecimal price = asset.getPrice();
            BigDecimal avgCost = holding.getAvgCostTry();

            BigDecimal value = price != null ? price.multiply(quantity) : BigDecimal.ZERO;
            BigDecimal cost = avgCost != null ? avgCost.multiply(quantity) : BigDecimal.ZERO;

            totalValue = totalValue.add(value);
            totalCost = totalCost.add(cost);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("quantity", quantity);
            entry.put("price", price);
            entry.put("avgCost", avgCost);
            entry.put("value", value);
            entry.put("cost", cost);
            holdingsJson.put(asset.getSymbol(), entry);
        }

        return new ValuedPortfolio(totalValue, totalCost, holdingsJson);
    }

    /** Result summary for a daily capture run. */
    public record CaptureResult(LocalDate date, int created, int updated) {}

    private record ValuedPortfolio(
            BigDecimal totalValue, BigDecimal totalCost, Map<String, Object> holdingsJson) {}
}
