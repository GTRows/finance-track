package com.fintrack.portfolio.rebalance;

import com.fintrack.account.AccountRepository;
import com.fintrack.asset.AssetRepository;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.PortfolioAllocationTarget;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.RebalanceConflictException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.allocation.AllocationTargetRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.rebalance.dto.RebalanceCommitRequest;
import com.fintrack.portfolio.rebalance.dto.RebalanceCommitResult;
import com.fintrack.portfolio.rebalance.dto.RebalancePreview;
import com.fintrack.portfolio.rebalance.dto.RebalancePreviewRequest;
import com.fintrack.portfolio.rebalance.dto.RebalanceSuggestion;
import com.fintrack.portfolio.transaction.InvestmentTransactionService;
import com.fintrack.portfolio.transaction.dto.RecordTransactionRequest;
import com.fintrack.portfolio.transaction.dto.TransactionResponse;
import com.fintrack.settings.UserSettingsRepository;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the rebalance preview + commit workflow. The preview computes per-bucket drift
 * suggestions, projects them onto individual holdings, applies cash scaling and quantity quirks,
 * stores a canonical hash in Redis (via {@link RebalanceProposalStore}), and returns a {@link
 * RebalancePreview} the operator ticks. The commit recomputes the suggestions from the live state,
 * compares hashes (rejecting on stale), and materialises one {@link InvestmentTransaction} per
 * ticked row through {@link InvestmentTransactionService#record} so the 25-01 holding projection
 * and 27-03 account-balance listeners pick up automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RebalanceService {

    static final BigDecimal DEFAULT_DRIFT_THRESHOLD = new BigDecimal("1.00");
    static final BigDecimal HUNDRED = new BigDecimal("100");
    static final int QUANTITY_SCALE = 8;
    static final int AMOUNT_SCALE = 4;
    static final Duration PROPOSAL_TTL = Duration.ofMinutes(30);

    static final String WARN_NO_HOLDING_TO_BUY = "NO_HOLDING_TO_BUY";
    static final String WARN_QUANTITY_BELOW_LOT = "QUANTITY_BELOW_LOT";
    static final String WARN_INSUFFICIENT_HOLDING = "INSUFFICIENT_HOLDING";
    static final String WARN_CASH_PARTIAL_SCALEDOWN = "CASH_PARTIAL_SCALEDOWN";
    static final String WARN_INSUFFICIENT_CASH = "INSUFFICIENT_CASH";

    private final AllocationTargetRepository targetRepository;
    private final HoldingRepository holdingRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final InvestmentTransactionService transactionService;
    private final RebalanceProposalStore proposalStore;
    private final AuditService auditService;

    @Observed(name = "portfolio.rebalance.preview", contextualName = "portfolio.rebalance.preview")
    @Transactional(readOnly = true)
    public RebalancePreview preview(
            UUID userId, UUID portfolioId, RebalancePreviewRequest request) {
        String username = currentUsername();
        requireOwnedPortfolio(userId, portfolioId, username);
        Account account = requireOwnedAccount(userId, request.accountId(), username);

        ComputeContext ctx =
                computeContext(
                        userId, portfolioId, account, request.driftThresholdOverride(), username);

        UUID proposalId = UUID.randomUUID();
        String hash = canonicalHash(portfolioId, request.accountId(), ctx.suggestions);
        proposalStore.putProposal(userId, proposalId, hash);

        auditService.success(
                AuditAction.REBALANCE_PREVIEWED,
                userId,
                username,
                "portfolioId="
                        + portfolioId
                        + " accountId="
                        + request.accountId()
                        + " proposalId="
                        + proposalId
                        + " suggestions="
                        + ctx.suggestions.size());

        return new RebalancePreview(
                proposalId,
                ctx.totalValue.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                ctx.availableCash.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                ctx.threshold,
                ctx.suggestions,
                ctx.projectedDriftAfter,
                ctx.summaryWarnings,
                Instant.now().plus(PROPOSAL_TTL));
    }

    private List<RebalanceSuggestion> computeSuggestions(
            UUID userId, UUID portfolioId, UUID accountId, BigDecimal override) {
        String username = currentUsername();
        Account account = requireOwnedAccount(userId, accountId, username);
        return computeContext(userId, portfolioId, account, override, username).suggestions;
    }

    private ComputeContext computeContext(
            UUID userId, UUID portfolioId, Account account, BigDecimal override, String username) {
        BigDecimal threshold = resolveThreshold(userId, override);

        List<PortfolioAllocationTarget> targets = targetRepository.findByPortfolioId(portfolioId);
        if (targets.isEmpty()) {
            auditService.failure(
                    AuditAction.REBALANCE_PREVIEWED, userId, username, "no targets configured");
            throw new BusinessRuleException("No allocation targets", "REBALANCE_NO_TARGETS");
        }

        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        Map<UUID, Asset> assetsById = loadAssets(holdings);

        Map<Asset.AssetType, BigDecimal> valueByType = computeValueByType(holdings, assetsById);
        BigDecimal totalValue =
                valueByType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Asset.AssetType, BigDecimal> targetByType = new EnumMap<>(Asset.AssetType.class);
        for (PortfolioAllocationTarget t : targets) {
            targetByType.put(t.getAssetType(), t.getTargetPercent());
        }

        List<RebalanceSuggestion> suggestions =
                projectBucketsOntoHoldings(
                        holdings, assetsById, valueByType, targetByType, totalValue, threshold);

        BigDecimal availableCash =
                account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO;
        List<String> summaryWarnings = new ArrayList<>();
        suggestions = applyCashScaling(suggestions, availableCash, summaryWarnings);
        suggestions = enforceQuantityQuirks(suggestions);
        suggestions = reindex(suggestions);

        BigDecimal projectedDriftAfter =
                computeProjectedDrift(suggestions, valueByType, targetByType, totalValue);

        return new ComputeContext(
                threshold,
                totalValue,
                availableCash,
                suggestions,
                summaryWarnings,
                projectedDriftAfter);
    }

    private record ComputeContext(
            BigDecimal threshold,
            BigDecimal totalValue,
            BigDecimal availableCash,
            List<RebalanceSuggestion> suggestions,
            List<String> summaryWarnings,
            BigDecimal projectedDriftAfter) {}

    @Observed(name = "portfolio.rebalance.commit", contextualName = "portfolio.rebalance.commit")
    @Transactional
    public RebalanceCommitResult commit(
            UUID userId, UUID portfolioId, RebalanceCommitRequest request) {
        String username = currentUsername();
        requireOwnedPortfolio(userId, portfolioId, username);
        requireOwnedAccount(userId, request.accountId(), username);

        if (proposalStore.isCommitted(userId, request.proposalId())) {
            auditService.failure(
                    AuditAction.REBALANCE_COMMITTED,
                    userId,
                    username,
                    "proposalId=" + request.proposalId() + " already committed");
            throw new RebalanceConflictException(
                    "Proposal already committed", "REBALANCE_PROPOSAL_ALREADY_COMMITTED");
        }
        String cachedHash =
                proposalStore
                        .getProposal(userId, request.proposalId())
                        .orElseThrow(
                                () -> {
                                    auditService.failure(
                                            AuditAction.REBALANCE_COMMITTED,
                                            userId,
                                            username,
                                            "proposalId=" + request.proposalId() + " not found");
                                    return new ResourceNotFoundException("Proposal not found");
                                });

        List<RebalanceSuggestion> liveSuggestions =
                computeSuggestions(userId, portfolioId, request.accountId(), null);
        String liveHash = canonicalHash(portfolioId, request.accountId(), liveSuggestions);
        if (!liveHash.equals(cachedHash)) {
            auditService.failure(
                    AuditAction.REBALANCE_COMMITTED,
                    userId,
                    username,
                    "proposalId=" + request.proposalId() + " stale");
            throw new RebalanceConflictException("Proposal is stale", "REBALANCE_PROPOSAL_STALE");
        }

        List<RebalanceSuggestion> selected =
                selectSuggestions(liveSuggestions, request.selectedIndices(), userId, username);

        List<UUID> committedIds = new ArrayList<>();
        for (RebalanceSuggestion suggestion : selected) {
            if (suggestion.assetId() == null
                    || suggestion.quantity() == null
                    || suggestion.quantity().signum() <= 0) {
                continue;
            }
            RecordTransactionRequest record =
                    new RecordTransactionRequest(
                            suggestion.assetId(),
                            suggestion.action(),
                            suggestion.quantity(),
                            suggestion.estimatedPriceTry(),
                            BigDecimal.ZERO,
                            LocalDate.now(),
                            "rebalance:" + request.proposalId(),
                            request.accountId());
            TransactionResponse response = transactionService.record(userId, portfolioId, record);
            committedIds.add(response.id());
        }

        proposalStore.markCommitted(userId, request.proposalId());

        auditService.success(
                AuditAction.REBALANCE_COMMITTED,
                userId,
                username,
                "portfolioId="
                        + portfolioId
                        + " accountId="
                        + request.accountId()
                        + " proposalId="
                        + request.proposalId()
                        + " selected="
                        + request.selectedIndices().size()
                        + " committed="
                        + committedIds.size());

        return new RebalanceCommitResult(request.proposalId(), committedIds.size(), committedIds);
    }

    BigDecimal resolveThreshold(UUID userId, BigDecimal override) {
        if (override != null) {
            return override.setScale(2, RoundingMode.HALF_UP);
        }
        UserSettings settings = userSettingsRepository.findById(userId).orElse(null);
        if (settings != null && settings.getRebalanceDriftThresholdPercent() != null) {
            return settings.getRebalanceDriftThresholdPercent().setScale(2, RoundingMode.HALF_UP);
        }
        return DEFAULT_DRIFT_THRESHOLD;
    }

    Map<Asset.AssetType, BigDecimal> computeValueByType(
            List<PortfolioHolding> holdings, Map<UUID, Asset> assetsById) {
        Map<Asset.AssetType, BigDecimal> totals = new EnumMap<>(Asset.AssetType.class);
        for (PortfolioHolding h : holdings) {
            Asset asset = assetsById.get(h.getAssetId());
            if (asset == null || asset.getPrice() == null || h.getQuantity() == null) continue;
            BigDecimal value = asset.getPrice().multiply(h.getQuantity());
            totals.merge(asset.getAssetType(), value, BigDecimal::add);
        }
        return totals;
    }

    /**
     * Bucket-to-holding projection. For each surviving bucket where {@code |drift| > threshold},
     * SELL is distributed pro-rata across holdings within the bucket; BUY is concentrated on the
     * highest-value holding (alphabetical tiebreak by symbol). Empty underweight buckets emit a
     * single {@code NO_HOLDING_TO_BUY} informational row.
     */
    List<RebalanceSuggestion> projectBucketsOntoHoldings(
            List<PortfolioHolding> holdings,
            Map<UUID, Asset> assetsById,
            Map<Asset.AssetType, BigDecimal> valueByType,
            Map<Asset.AssetType, BigDecimal> targetByType,
            BigDecimal totalValue,
            BigDecimal threshold) {
        List<RebalanceSuggestion> suggestions = new ArrayList<>();
        if (totalValue.signum() == 0) {
            return suggestions;
        }

        Set<Asset.AssetType> types = new TreeSet<>();
        types.addAll(valueByType.keySet());
        types.addAll(targetByType.keySet());

        Map<Asset.AssetType, List<PortfolioHolding>> holdingsByType =
                new EnumMap<>(Asset.AssetType.class);
        for (PortfolioHolding h : holdings) {
            Asset asset = assetsById.get(h.getAssetId());
            if (asset == null) continue;
            holdingsByType.computeIfAbsent(asset.getAssetType(), k -> new ArrayList<>()).add(h);
        }

        for (Asset.AssetType type : types) {
            BigDecimal target =
                    targetByType
                            .getOrDefault(type, BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
            BigDecimal currentValue = valueByType.getOrDefault(type, BigDecimal.ZERO);
            BigDecimal currentPercent =
                    currentValue.multiply(HUNDRED).divide(totalValue, 2, RoundingMode.HALF_UP);
            BigDecimal driftPercent = currentPercent.subtract(target);
            if (driftPercent.abs().compareTo(threshold) <= 0) {
                continue;
            }
            BigDecimal targetValue =
                    totalValue.multiply(target).divide(HUNDRED, AMOUNT_SCALE, RoundingMode.HALF_UP);
            BigDecimal deltaTry = targetValue.subtract(currentValue);

            List<PortfolioHolding> bucketHoldings = holdingsByType.getOrDefault(type, List.of());

            if (deltaTry.signum() < 0) {
                BigDecimal sellTotal = deltaTry.abs();
                addSellSuggestions(
                        suggestions,
                        bucketHoldings,
                        assetsById,
                        sellTotal,
                        currentValue,
                        currentPercent,
                        target,
                        driftPercent);
            } else if (deltaTry.signum() > 0) {
                addBuySuggestion(
                        suggestions,
                        bucketHoldings,
                        assetsById,
                        type,
                        deltaTry,
                        currentPercent,
                        target,
                        driftPercent);
            }
        }
        return suggestions;
    }

    private void addSellSuggestions(
            List<RebalanceSuggestion> suggestions,
            List<PortfolioHolding> bucketHoldings,
            Map<UUID, Asset> assetsById,
            BigDecimal sellTotal,
            BigDecimal currentValue,
            BigDecimal currentPercent,
            BigDecimal targetPercent,
            BigDecimal driftPercent) {
        if (currentValue.signum() == 0 || bucketHoldings.isEmpty()) return;
        for (PortfolioHolding h : bucketHoldings) {
            Asset asset = assetsById.get(h.getAssetId());
            if (asset == null || asset.getPrice() == null || asset.getPrice().signum() <= 0)
                continue;
            BigDecimal holdingValue = asset.getPrice().multiply(h.getQuantity());
            if (holdingValue.signum() <= 0) continue;
            BigDecimal share = holdingValue.divide(currentValue, 8, RoundingMode.HALF_UP);
            BigDecimal sellAmount = sellTotal.multiply(share);
            if (sellAmount.compareTo(holdingValue) > 0) {
                sellAmount = holdingValue;
            }
            BigDecimal quantity =
                    sellAmount.divide(asset.getPrice(), QUANTITY_SCALE, RoundingMode.DOWN);
            if (quantity.signum() <= 0) continue;
            if (quantity.compareTo(h.getQuantity()) > 0) {
                quantity = h.getQuantity();
            }
            BigDecimal estimatedAmount =
                    quantity.multiply(asset.getPrice())
                            .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            suggestions.add(
                    new RebalanceSuggestion(
                            suggestions.size(),
                            asset.getId(),
                            asset.getSymbol(),
                            asset.getAssetType(),
                            InvestmentTransaction.TxnType.SELL,
                            quantity,
                            asset.getPrice().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                            estimatedAmount,
                            holdingValue.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                            currentPercent,
                            targetPercent,
                            driftPercent,
                            null));
        }
    }

    private void addBuySuggestion(
            List<RebalanceSuggestion> suggestions,
            List<PortfolioHolding> bucketHoldings,
            Map<UUID, Asset> assetsById,
            Asset.AssetType type,
            BigDecimal buyAmount,
            BigDecimal currentPercent,
            BigDecimal targetPercent,
            BigDecimal driftPercent) {
        if (bucketHoldings.isEmpty()) {
            suggestions.add(
                    new RebalanceSuggestion(
                            suggestions.size(),
                            null,
                            null,
                            type,
                            InvestmentTransaction.TxnType.BUY,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            currentPercent,
                            targetPercent,
                            driftPercent,
                            WARN_NO_HOLDING_TO_BUY));
            return;
        }
        PortfolioHolding target =
                bucketHoldings.stream()
                        .filter(
                                h -> {
                                    Asset a = assetsById.get(h.getAssetId());
                                    return a != null
                                            && a.getPrice() != null
                                            && a.getPrice().signum() > 0;
                                })
                        .max(
                                Comparator.comparing(
                                                (PortfolioHolding h) -> {
                                                    Asset a = assetsById.get(h.getAssetId());
                                                    return a.getPrice().multiply(h.getQuantity());
                                                })
                                        .thenComparing(
                                                h -> assetsById.get(h.getAssetId()).getSymbol(),
                                                Comparator.reverseOrder()))
                        .orElse(null);
        if (target == null) {
            return;
        }
        Asset asset = assetsById.get(target.getAssetId());
        BigDecimal quantity = buyAmount.divide(asset.getPrice(), QUANTITY_SCALE, RoundingMode.DOWN);
        BigDecimal estimatedAmount =
                quantity.multiply(asset.getPrice()).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal currentHoldingValue = asset.getPrice().multiply(target.getQuantity());
        suggestions.add(
                new RebalanceSuggestion(
                        suggestions.size(),
                        asset.getId(),
                        asset.getSymbol(),
                        asset.getAssetType(),
                        InvestmentTransaction.TxnType.BUY,
                        quantity,
                        asset.getPrice().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                        estimatedAmount,
                        currentHoldingValue.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                        currentPercent,
                        targetPercent,
                        driftPercent,
                        null));
    }

    /**
     * Proportionally scale BUY rows when the total required cash exceeds the account balance. SELL
     * rows are never scaled because they replenish cash. Adds {@code CASH_PARTIAL_SCALEDOWN} or
     * {@code INSUFFICIENT_CASH} summary warnings depending on the cash position.
     */
    List<RebalanceSuggestion> applyCashScaling(
            List<RebalanceSuggestion> suggestions,
            BigDecimal availableCash,
            List<String> summaryWarnings) {
        BigDecimal requiredBuy = BigDecimal.ZERO;
        for (RebalanceSuggestion s : suggestions) {
            if (s.action() == InvestmentTransaction.TxnType.BUY
                    && s.estimatedAmountTry() != null
                    && s.estimatedAmountTry().signum() > 0) {
                requiredBuy = requiredBuy.add(s.estimatedAmountTry());
            }
        }
        if (requiredBuy.signum() == 0) {
            return suggestions;
        }
        if (availableCash.signum() <= 0) {
            summaryWarnings.add(WARN_INSUFFICIENT_CASH);
            return scaleBuys(suggestions, BigDecimal.ZERO);
        }
        if (requiredBuy.compareTo(availableCash) > 0) {
            BigDecimal ratio = availableCash.divide(requiredBuy, 10, RoundingMode.DOWN);
            summaryWarnings.add(WARN_CASH_PARTIAL_SCALEDOWN);
            return scaleBuys(suggestions, ratio);
        }
        return suggestions;
    }

    private List<RebalanceSuggestion> scaleBuys(
            List<RebalanceSuggestion> suggestions, BigDecimal ratio) {
        List<RebalanceSuggestion> out = new ArrayList<>(suggestions.size());
        for (RebalanceSuggestion s : suggestions) {
            if (s.action() != InvestmentTransaction.TxnType.BUY
                    || s.estimatedPriceTry() == null
                    || s.estimatedPriceTry().signum() <= 0
                    || s.assetId() == null) {
                out.add(s);
                continue;
            }
            BigDecimal scaledQty =
                    s.quantity().multiply(ratio).setScale(QUANTITY_SCALE, RoundingMode.DOWN);
            BigDecimal scaledAmount =
                    scaledQty
                            .multiply(s.estimatedPriceTry())
                            .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            out.add(
                    new RebalanceSuggestion(
                            s.index(),
                            s.assetId(),
                            s.symbol(),
                            s.assetType(),
                            s.action(),
                            scaledQty,
                            s.estimatedPriceTry(),
                            scaledAmount,
                            s.currentValueTry(),
                            s.currentWeightPercent(),
                            s.targetWeightPercent(),
                            s.driftPercentBefore(),
                            s.warning()));
        }
        return out;
    }

    /**
     * STOCK and FUND rows are forced to integer quantities (RoundingMode.DOWN). When truncation
     * drops the row to zero, attach a {@code QUANTITY_BELOW_LOT} warning so the operator knows the
     * bucket is too close to target for the integer-lot constraint.
     */
    List<RebalanceSuggestion> enforceQuantityQuirks(List<RebalanceSuggestion> suggestions) {
        List<RebalanceSuggestion> out = new ArrayList<>(suggestions.size());
        for (RebalanceSuggestion s : suggestions) {
            if (s.assetType() != Asset.AssetType.STOCK && s.assetType() != Asset.AssetType.FUND) {
                out.add(s);
                continue;
            }
            if (s.quantity() == null || s.estimatedPriceTry() == null || s.assetId() == null) {
                out.add(s);
                continue;
            }
            BigDecimal truncated = s.quantity().setScale(0, RoundingMode.DOWN);
            BigDecimal newAmount =
                    truncated
                            .multiply(s.estimatedPriceTry())
                            .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            String warning = s.warning();
            if (truncated.signum() == 0 && warning == null) {
                warning = WARN_QUANTITY_BELOW_LOT;
            }
            out.add(
                    new RebalanceSuggestion(
                            s.index(),
                            s.assetId(),
                            s.symbol(),
                            s.assetType(),
                            s.action(),
                            truncated,
                            s.estimatedPriceTry(),
                            newAmount,
                            s.currentValueTry(),
                            s.currentWeightPercent(),
                            s.targetWeightPercent(),
                            s.driftPercentBefore(),
                            warning));
        }
        return out;
    }

    private List<RebalanceSuggestion> reindex(List<RebalanceSuggestion> suggestions) {
        List<RebalanceSuggestion> out = new ArrayList<>(suggestions.size());
        int i = 0;
        for (RebalanceSuggestion s : suggestions) {
            out.add(
                    new RebalanceSuggestion(
                            i++,
                            s.assetId(),
                            s.symbol(),
                            s.assetType(),
                            s.action(),
                            s.quantity(),
                            s.estimatedPriceTry(),
                            s.estimatedAmountTry(),
                            s.currentValueTry(),
                            s.currentWeightPercent(),
                            s.targetWeightPercent(),
                            s.driftPercentBefore(),
                            s.warning()));
        }
        return out;
    }

    BigDecimal computeProjectedDrift(
            List<RebalanceSuggestion> suggestions,
            Map<Asset.AssetType, BigDecimal> valueByType,
            Map<Asset.AssetType, BigDecimal> targetByType,
            BigDecimal totalValue) {
        if (totalValue.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Map<Asset.AssetType, BigDecimal> projected = new EnumMap<>(Asset.AssetType.class);
        projected.putAll(valueByType);
        for (RebalanceSuggestion s : suggestions) {
            if (s.estimatedAmountTry() == null) continue;
            BigDecimal current = projected.getOrDefault(s.assetType(), BigDecimal.ZERO);
            BigDecimal next =
                    s.action() == InvestmentTransaction.TxnType.BUY
                            ? current.add(s.estimatedAmountTry())
                            : current.subtract(s.estimatedAmountTry());
            projected.put(s.assetType(), next);
        }
        Set<Asset.AssetType> types = new TreeSet<>();
        types.addAll(projected.keySet());
        types.addAll(targetByType.keySet());
        BigDecimal driftSum = BigDecimal.ZERO;
        for (Asset.AssetType t : types) {
            BigDecimal target = targetByType.getOrDefault(t, BigDecimal.ZERO);
            BigDecimal value = projected.getOrDefault(t, BigDecimal.ZERO);
            BigDecimal pct = value.multiply(HUNDRED).divide(totalValue, 2, RoundingMode.HALF_UP);
            driftSum = driftSum.add(pct.subtract(target).abs());
        }
        return driftSum.setScale(2, RoundingMode.HALF_UP);
    }

    String canonicalHash(UUID portfolioId, UUID accountId, List<RebalanceSuggestion> suggestions) {
        StringBuilder sb = new StringBuilder();
        sb.append(portfolioId).append('|').append(accountId);
        for (RebalanceSuggestion s : suggestions) {
            sb.append('|')
                    .append(s.assetId())
                    .append(':')
                    .append(s.action())
                    .append(':')
                    .append(
                            s.quantity() == null
                                    ? "0"
                                    : s.quantity().stripTrailingZeros().toPlainString())
                    .append(':')
                    .append(
                            s.estimatedPriceTry() == null
                                    ? "0"
                                    : s.estimatedPriceTry().stripTrailingZeros().toPlainString());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private List<RebalanceSuggestion> selectSuggestions(
            List<RebalanceSuggestion> suggestions,
            List<Integer> selectedIndices,
            UUID userId,
            String username) {
        Set<Integer> seen = new HashSet<>();
        List<RebalanceSuggestion> selected = new ArrayList<>();
        for (Integer idx : selectedIndices) {
            if (idx == null || idx < 0 || idx >= suggestions.size() || !seen.add(idx)) {
                auditService.failure(
                        AuditAction.REBALANCE_COMMITTED,
                        userId,
                        username,
                        "selection out of range: " + idx);
                throw new BusinessRuleException(
                        "Selection out of range", "REBALANCE_SELECTION_OUT_OF_RANGE");
            }
            selected.add(suggestions.get(idx));
        }
        return selected;
    }

    private void requireOwnedPortfolio(UUID userId, UUID portfolioId, String username) {
        portfolioRepository
                .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(
                        () -> {
                            auditService.failure(
                                    AuditAction.REBALANCE_PREVIEWED,
                                    userId,
                                    username,
                                    "portfolioId=" + portfolioId + " not found");
                            return new ResourceNotFoundException("Portfolio not found");
                        });
    }

    private Account requireOwnedAccount(UUID userId, UUID accountId, String username) {
        return accountRepository
                .findByIdAndUserIdAndArchivedFalse(accountId, userId)
                .orElseThrow(
                        () -> {
                            auditService.failure(
                                    AuditAction.REBALANCE_PREVIEWED,
                                    userId,
                                    username,
                                    "accountId=" + accountId + " not found");
                            return new BusinessRuleException(
                                    "Account not found", "ACCOUNT_NOT_OWNED");
                        });
    }

    private Map<UUID, Asset> loadAssets(List<PortfolioHolding> holdings) {
        Set<UUID> ids = new HashSet<>();
        for (PortfolioHolding h : holdings) ids.add(h.getAssetId());
        if (ids.isEmpty()) return new HashMap<>();
        Map<UUID, Asset> byId = new HashMap<>();
        assetRepository.findAllById(ids).forEach(a -> byId.put(a.getId(), a));
        return byId;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
