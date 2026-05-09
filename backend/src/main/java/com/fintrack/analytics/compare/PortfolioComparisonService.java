package com.fintrack.analytics.compare;

import com.fintrack.analytics.compare.dto.PortfolioComparisonPoint;
import com.fintrack.analytics.compare.dto.PortfolioComparisonResponse;
import com.fintrack.analytics.compare.dto.PortfolioComparisonSeries;
import com.fintrack.common.config.CacheConfig;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import com.fintrack.portfolio.transaction.InvestmentTransactionRepository;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only orchestrator for the multi-portfolio comparison view. Pulls daily snapshots and SELL
 * transactions for each requested portfolio, then folds them into a TRY-denominated time series
 * with realised + unrealised P&L per snapshot date.
 *
 * <p>Realised P&L is the running-average approximation: {@code sum((sellPriceTry -
 * currentHoldingAvgCostTry) * quantity)} for SELL rows whose {@code txnDate <= date}. This trades
 * FIFO precision for orchestration simplicity; the precise lot-level capital-gains report lives at
 * {@code /api/v1/reports/capital-gains}.
 */
@Service
@RequiredArgsConstructor
public class PortfolioComparisonService {

    /** Hard cap on the number of portfolios per compare request (UI legend + SQL fan-out). */
    public static final int MAX_PORTFOLIOS = 10;

    private static final String CURRENCY_TRY = "TRY";

    private final PortfolioRepository portfolioRepository;
    private final SnapshotRepository snapshotRepository;
    private final InvestmentTransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;

    /**
     * Returns a TRY-denominated comparison response for the requested portfolios over the
     * (optional) date range. Cached on a sorted-id key so {@code [A,B]} and {@code [B,A]} share an
     * entry.
     */
    @Observed(name = "analytics.portfolios.compare", contextualName = "compare")
    @Cacheable(
            value = CacheConfig.ANALYTICS_PORTFOLIOS_COMPARE_CACHE,
            key =
                    "#userId + ':' + T(java.lang.String).join(',',"
                            + " #ids.stream().map(T(java.util.UUID)::toString).sorted().toList()) +"
                            + " ':' + (#from != null ? #from.toString() : 'null') + ':' + (#to !="
                            + " null ? #to.toString() : 'null')")
    @Transactional(readOnly = true)
    public PortfolioComparisonResponse compare(
            UUID userId, List<UUID> ids, LocalDate from, LocalDate to) {
        List<UUID> deduped = dedupePreserveOrder(ids);
        if (deduped.isEmpty()) {
            throw new BusinessRuleException("ids required", "COMPARE_IDS_REQUIRED");
        }
        if (deduped.size() > MAX_PORTFOLIOS) {
            throw new BusinessRuleException(
                    "Too many portfolios in compare request", "COMPARE_TOO_MANY");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("Invalid range", "COMPARE_RANGE_INVALID");
        }

        List<PortfolioComparisonSeries> seriesList = new ArrayList<>(deduped.size());
        for (UUID portfolioId : deduped) {
            Portfolio portfolio =
                    portfolioRepository
                            .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Portfolio not found: " + portfolioId));
            seriesList.add(buildSeries(portfolio, from, to));
        }

        return new PortfolioComparisonResponse(CURRENCY_TRY, seriesList);
    }

    private PortfolioComparisonSeries buildSeries(
            Portfolio portfolio, LocalDate from, LocalDate to) {
        List<PortfolioSnapshot> snapshots = loadSnapshots(portfolio.getId(), from, to);
        if (snapshots.isEmpty()) {
            return new PortfolioComparisonSeries(portfolio.getId(), portfolio.getName(), List.of());
        }

        LocalDate effectiveTo =
                to != null ? to : snapshots.get(snapshots.size() - 1).getSnapshotDate();
        List<InvestmentTransaction> sells =
                transactionRepository
                        .findByPortfolioIdAndTxnTypeAndTxnDateLessThanEqualOrderByTxnDateAsc(
                                portfolio.getId(), InvestmentTransaction.TxnType.SELL, effectiveTo);

        Map<UUID, BigDecimal> avgCostByAsset = currentAvgCostByAsset(portfolio.getId());

        List<PortfolioComparisonPoint> points = new ArrayList<>(snapshots.size());
        for (PortfolioSnapshot snapshot : snapshots) {
            BigDecimal value = nvl(snapshot.getTotalValueTry());
            BigDecimal cost = nvl(snapshot.getTotalCostTry());
            BigDecimal unrealized = value.subtract(cost);
            BigDecimal realized = realizedPnlAt(sells, snapshot.getSnapshotDate(), avgCostByAsset);
            BigDecimal total = unrealized.add(realized);
            points.add(
                    new PortfolioComparisonPoint(
                            snapshot.getSnapshotDate(), value, cost, unrealized, realized, total));
        }

        return new PortfolioComparisonSeries(portfolio.getId(), portfolio.getName(), points);
    }

    private List<PortfolioSnapshot> loadSnapshots(UUID portfolioId, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);
        }
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(9999, 12, 31);
        return snapshotRepository.findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                portfolioId, effectiveFrom, effectiveTo);
    }

    private Map<UUID, BigDecimal> currentAvgCostByAsset(UUID portfolioId) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (PortfolioHolding holding : holdingRepository.findByPortfolioId(portfolioId)) {
            BigDecimal avg = holding.getAvgCostTry();
            map.put(holding.getAssetId(), avg != null ? avg : BigDecimal.ZERO);
        }
        return map;
    }

    private BigDecimal realizedPnlAt(
            List<InvestmentTransaction> sells,
            LocalDate cutoff,
            Map<UUID, BigDecimal> avgCostByAsset) {
        BigDecimal sum = BigDecimal.ZERO;
        for (InvestmentTransaction sell : sells) {
            if (sell.getTxnDate() == null || sell.getTxnDate().isAfter(cutoff)) {
                continue;
            }
            BigDecimal qty = nvl(sell.getQuantity());
            BigDecimal price = nvl(sell.getPriceTry());
            BigDecimal avg = avgCostByAsset.getOrDefault(sell.getAssetId(), BigDecimal.ZERO);
            sum = sum.add(price.subtract(avg).multiply(qty));
        }
        return sum;
    }

    private static List<UUID> dedupePreserveOrder(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
