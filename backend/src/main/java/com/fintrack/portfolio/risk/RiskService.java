package com.fintrack.portfolio.risk;

import com.fintrack.common.entity.PortfolioSnapshot;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.risk.dto.RiskMetricsResponse;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Derives common risk/return indicators from the daily portfolio snapshots
 * captured by {@link com.fintrack.portfolio.snapshot.SnapshotScheduler}.
 *
 * Daily returns are computed as <code>r_i = V_i/V_{i-1} - 1</code>. Values
 * are expressed as decimals so that the frontend can format them freely.
 */
@Service
@RequiredArgsConstructor
public class RiskService {

    /** Trading days per year used to annualise volatility and Sharpe. */
    private static final int TRADING_DAYS = 252;

    /** Minimum number of daily returns required before risk stats are meaningful. */
    private static final int MIN_RETURNS_FOR_RISK = 20;

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private final SnapshotRepository snapshotRepository;
    private final PortfolioRepository portfolioRepository;

    @Transactional(readOnly = true)
    public RiskMetricsResponse compute(UUID userId, UUID portfolioId, BigDecimal annualRiskFreeRate) {
        portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));

        BigDecimal rf = annualRiskFreeRate != null ? annualRiskFreeRate : BigDecimal.ZERO;

        List<PortfolioSnapshot> snapshots = snapshotRepository
                .findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);

        if (snapshots.isEmpty()) {
            return RiskMetricsResponse.insufficient(0, null, null, rf);
        }

        List<BigDecimal> returns = dailyReturns(snapshots);

        if (returns.size() < MIN_RETURNS_FOR_RISK) {
            return RiskMetricsResponse.insufficient(
                    snapshots.size(),
                    snapshots.get(0).getSnapshotDate(),
                    snapshots.get(snapshots.size() - 1).getSnapshotDate(),
                    rf
            );
        }

        BigDecimal mean = mean(returns);
        BigDecimal stdev = sampleStdDev(returns, mean);
        BigDecimal annualVol = stdev.multiply(BigDecimal.valueOf(Math.sqrt(TRADING_DAYS)), MC);

        BigDecimal dailyRf = rf.divide(BigDecimal.valueOf(TRADING_DAYS), MC);
        BigDecimal sharpe = stdev.signum() == 0
                ? BigDecimal.ZERO
                : mean.subtract(dailyRf).divide(stdev, MC).multiply(BigDecimal.valueOf(Math.sqrt(TRADING_DAYS)), MC);

        BigDecimal drawdown = maxDrawdown(snapshots);

        BigDecimal best = returns.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal worst = returns.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        BigDecimal totalReturn = totalReturn(snapshots);

        return new RiskMetricsResponse(
                snapshots.size(),
                snapshots.get(0).getSnapshotDate(),
                snapshots.get(snapshots.size() - 1).getSnapshotDate(),
                scale(totalReturn),
                scale(annualVol),
                scale(sharpe),
                scale(drawdown),
                scale(best),
                scale(worst),
                scale(mean),
                rf,
                true
        );
    }

    private static List<BigDecimal> dailyReturns(List<PortfolioSnapshot> snapshots) {
        List<BigDecimal> out = new ArrayList<>(snapshots.size());
        for (int i = 1; i < snapshots.size(); i++) {
            BigDecimal prev = snapshots.get(i - 1).getTotalValueTry();
            BigDecimal cur = snapshots.get(i).getTotalValueTry();
            if (prev == null || cur == null || prev.signum() == 0) {
                continue;
            }
            out.add(cur.divide(prev, MC).subtract(BigDecimal.ONE));
        }
        return out;
    }

    private static BigDecimal totalReturn(List<PortfolioSnapshot> snapshots) {
        BigDecimal first = snapshots.get(0).getTotalValueTry();
        BigDecimal last = snapshots.get(snapshots.size() - 1).getTotalValueTry();
        if (first == null || last == null || first.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return last.divide(first, MC).subtract(BigDecimal.ONE);
    }

    private static BigDecimal maxDrawdown(List<PortfolioSnapshot> snapshots) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal worst = BigDecimal.ZERO;
        for (PortfolioSnapshot s : snapshots) {
            BigDecimal v = s.getTotalValueTry();
            if (v == null) continue;
            if (v.compareTo(peak) > 0) peak = v;
            if (peak.signum() > 0) {
                BigDecimal dd = v.subtract(peak).divide(peak, MC);
                if (dd.compareTo(worst) < 0) worst = dd;
            }
        }
        return worst;
    }

    private static BigDecimal mean(List<BigDecimal> xs) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal x : xs) sum = sum.add(x);
        return sum.divide(BigDecimal.valueOf(xs.size()), MC);
    }

    private static BigDecimal sampleStdDev(List<BigDecimal> xs, BigDecimal mean) {
        if (xs.size() < 2) return BigDecimal.ZERO;
        BigDecimal sumSq = BigDecimal.ZERO;
        for (BigDecimal x : xs) {
            BigDecimal d = x.subtract(mean);
            sumSq = sumSq.add(d.multiply(d, MC));
        }
        BigDecimal variance = sumSq.divide(BigDecimal.valueOf(xs.size() - 1L), MC);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(6, RoundingMode.HALF_UP);
    }
}
