package com.fintrack.report.capitalgains;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.dividend.DividendRepository;
import com.fintrack.portfolio.transaction.InvestmentTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CapitalGainsService {

    private static final int COST_SCALE = 8;

    private final PortfolioRepository portfolioRepo;
    private final InvestmentTransactionRepository txnRepo;
    private final AssetRepository assetRepo;
    private final DividendRepository dividendRepo;

    @Transactional(readOnly = true)
    public CapitalGainsResponse compute(UUID userId, Integer yearFilter) {
        List<Portfolio> portfolios =
                portfolioRepo.findByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);
        if (portfolios.isEmpty()) {
            return new CapitalGainsResponse(
                    yearFilter,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of(),
                    List.of());
        }
        Map<UUID, Portfolio> portfolioById = new HashMap<>();
        for (Portfolio p : portfolios) portfolioById.put(p.getId(), p);

        List<InvestmentTransaction> allTxns = new ArrayList<>();
        for (Portfolio p : portfolios) {
            allTxns.addAll(txnRepo.findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(p.getId()));
        }
        allTxns.sort(
                Comparator.comparing(InvestmentTransaction::getTxnDate)
                        .thenComparing(
                                InvestmentTransaction::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())));

        Map<UUID, Asset> assetCache = new HashMap<>();
        Map<Key, Lot> lots = new HashMap<>();
        List<CapitalGainsResponse.Event> events = new ArrayList<>();
        Map<Integer, YearAgg> yearAggs = new TreeMap<>();

        for (InvestmentTransaction txn : allTxns) {
            Key key = new Key(txn.getPortfolioId(), txn.getAssetId());
            Lot lot = lots.computeIfAbsent(key, k -> new Lot());

            if (txn.getTxnType() == TxnType.BUY || txn.getTxnType() == TxnType.BES_CONTRIBUTION) {
                BigDecimal qty = nullToZero(txn.getQuantity());
                BigDecimal amount = nullToZero(txn.getAmountTry());
                if (qty.signum() > 0) {
                    lot.quantity = lot.quantity.add(qty);
                    lot.totalCost = lot.totalCost.add(amount);
                }
            } else if (txn.getTxnType() == TxnType.SELL) {
                BigDecimal qty = nullToZero(txn.getQuantity());
                BigDecimal amount = nullToZero(txn.getAmountTry());
                BigDecimal fee = nullToZero(txn.getFeeTry());
                if (qty.signum() <= 0 || lot.quantity.signum() <= 0) continue;

                BigDecimal sellQty = qty.min(lot.quantity);
                BigDecimal avgCost =
                        lot.quantity.signum() == 0
                                ? BigDecimal.ZERO
                                : lot.totalCost.divide(
                                        lot.quantity, COST_SCALE, RoundingMode.HALF_UP);
                BigDecimal costBasis = avgCost.multiply(sellQty).setScale(4, RoundingMode.HALF_UP);
                BigDecimal proceedsGross = amount.add(fee);
                BigDecimal proceedsNet = amount;
                BigDecimal gain = proceedsNet.subtract(costBasis);

                lot.totalCost = lot.totalCost.subtract(costBasis);
                lot.quantity = lot.quantity.subtract(sellQty);
                if (lot.quantity.signum() <= 0) {
                    lot.quantity = BigDecimal.ZERO;
                    lot.totalCost = BigDecimal.ZERO;
                }

                int year = txn.getTxnDate().getYear();
                if (yearFilter != null && yearFilter != year) continue;

                Asset asset =
                        assetCache.computeIfAbsent(
                                txn.getAssetId(), id -> assetRepo.findById(id).orElse(null));
                Portfolio portfolio = portfolioById.get(txn.getPortfolioId());

                BigDecimal pricePerUnit =
                        txn.getPriceTry() != null
                                ? txn.getPriceTry()
                                : (sellQty.signum() > 0
                                        ? proceedsGross.divide(sellQty, 4, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO);

                events.add(
                        new CapitalGainsResponse.Event(
                                txn.getId(),
                                txn.getPortfolioId(),
                                portfolio != null ? portfolio.getName() : null,
                                txn.getAssetId(),
                                asset != null ? asset.getSymbol() : null,
                                asset != null ? asset.getName() : null,
                                txn.getTxnDate(),
                                sellQty,
                                pricePerUnit,
                                proceedsGross,
                                costBasis,
                                fee,
                                gain));

                YearAgg agg = yearAggs.computeIfAbsent(year, y -> new YearAgg());
                agg.proceeds = agg.proceeds.add(proceedsGross);
                agg.costBasis = agg.costBasis.add(costBasis);
                agg.fees = agg.fees.add(fee);
                agg.realized = agg.realized.add(gain);
                agg.count++;
            }
        }

        List<UUID> portfolioIds = portfolios.stream().map(Portfolio::getId).toList();
        for (Map.Entry<Integer, YearAgg> entry : yearAggs.entrySet()) {
            int year = entry.getKey();
            BigDecimal divNet =
                    dividendRepo.sumNetByPortfoliosAndRange(
                            portfolioIds, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            entry.getValue().dividendsNetTry = divNet != null ? divNet : BigDecimal.ZERO;
        }

        BigDecimal totalProceeds = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalRealized = BigDecimal.ZERO;
        BigDecimal totalDividendsTry = BigDecimal.ZERO;
        List<CapitalGainsResponse.YearSummary> byYear = new ArrayList<>();
        for (Map.Entry<Integer, YearAgg> entry : yearAggs.entrySet()) {
            YearAgg agg = entry.getValue();
            byYear.add(
                    new CapitalGainsResponse.YearSummary(
                            entry.getKey(),
                            agg.proceeds,
                            agg.costBasis,
                            agg.fees,
                            agg.realized,
                            agg.dividendsNetTry,
                            agg.count));
            totalProceeds = totalProceeds.add(agg.proceeds);
            totalCost = totalCost.add(agg.costBasis);
            totalFees = totalFees.add(agg.fees);
            totalRealized = totalRealized.add(agg.realized);
            totalDividendsTry = totalDividendsTry.add(agg.dividendsNetTry);
        }

        if (yearFilter != null) {
            BigDecimal divNet =
                    dividendRepo.sumNetByPortfoliosAndRange(
                            portfolioIds,
                            LocalDate.of(yearFilter, 1, 1),
                            LocalDate.of(yearFilter, 12, 31));
            totalDividendsTry = divNet != null ? divNet : BigDecimal.ZERO;
        }

        events.sort(Comparator.comparing(CapitalGainsResponse.Event::txnDate).reversed());

        return new CapitalGainsResponse(
                yearFilter,
                totalProceeds,
                totalCost,
                totalFees,
                totalRealized,
                totalDividendsTry,
                byYear,
                events);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Key(UUID portfolioId, UUID assetId) {}

    private static final class Lot {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
    }

    private static final class YearAgg {
        BigDecimal proceeds = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal dividendsNetTry = BigDecimal.ZERO;
        int count = 0;
    }
}
