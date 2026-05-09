package com.fintrack.portfolio.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.account.AccountRepository;
import com.fintrack.asset.AssetRepository;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.Portfolio;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RebalanceServiceTest {

    @Mock AllocationTargetRepository targetRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock AssetRepository assetRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock AccountRepository accountRepository;
    @Mock UserSettingsRepository userSettingsRepository;
    @Mock InvestmentTransactionService transactionService;
    @Mock RebalanceProposalStore proposalStore;
    @Mock AuditService auditService;

    @InjectMocks RebalanceService service;

    UUID userId = UUID.randomUUID();
    UUID portfolioId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    UUID stockAssetA = UUID.randomUUID();
    UUID stockAssetB = UUID.randomUUID();
    UUID cryptoAsset = UUID.randomUUID();
    UUID fundAsset = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(
                        Optional.of(Portfolio.builder().id(portfolioId).userId(userId).build()));
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(accountId, userId))
                .thenReturn(
                        Optional.of(
                                Account.builder()
                                        .id(accountId)
                                        .userId(userId)
                                        .currentBalance(new BigDecimal("100000"))
                                        .build()));
        when(userSettingsRepository.findById(userId))
                .thenReturn(
                        Optional.of(
                                UserSettings.builder()
                                        .userId(userId)
                                        .rebalanceDriftThresholdPercent(new BigDecimal("1.00"))
                                        .build()));
    }

    private Asset stockAsset(UUID id, String symbol, BigDecimal price) {
        return Asset.builder()
                .id(id)
                .symbol(symbol)
                .name(symbol)
                .assetType(Asset.AssetType.STOCK)
                .price(price)
                .build();
    }

    private Asset cryptoAsset(UUID id, String symbol, BigDecimal price) {
        return Asset.builder()
                .id(id)
                .symbol(symbol)
                .name(symbol)
                .assetType(Asset.AssetType.CRYPTO)
                .price(price)
                .build();
    }

    private Asset fundAsset(UUID id, String symbol, BigDecimal price) {
        return Asset.builder()
                .id(id)
                .symbol(symbol)
                .name(symbol)
                .assetType(Asset.AssetType.FUND)
                .price(price)
                .build();
    }

    private PortfolioHolding holding(UUID assetId, BigDecimal quantity) {
        return PortfolioHolding.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .assetId(assetId)
                .quantity(quantity)
                .build();
    }

    private PortfolioAllocationTarget target(Asset.AssetType type, String pct) {
        return PortfolioAllocationTarget.builder()
                .portfolioId(portfolioId)
                .assetType(type)
                .targetPercent(new BigDecimal(pct))
                .build();
    }

    @Test
    void preview_throwsWhenNoTargetsConfigured() {
        when(targetRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());

        RebalancePreviewRequest request = new RebalancePreviewRequest(accountId, null);

        assertThatThrownBy(() -> service.preview(userId, portfolioId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("REBALANCE_NO_TARGETS");
    }

    @Test
    void preview_returnsEmptyWhenAllBucketsWithinThreshold() {
        // 50/50 stock/crypto target, holdings already at 50/50 -> no suggestions
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "50.00"),
                                target(Asset.AssetType.CRYPTO, "50.00")));
        Asset stock = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset crypto = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("10"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(stock, crypto));

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        assertThat(preview.suggestions()).isEmpty();
    }

    @Test
    void preview_proRataSellAcrossOverweightBucket() {
        // STOCK target 40%, holdings AAA 100*10=1000 + BBB 200*5=1000, total stock 2000
        // CRYPTO target 60%, holding BTC 100*10=1000 -> total 3000, stock actual 66.67%
        // overweight stock by ~26.67pp -> sell stock value ~ (2000 - 3000*0.4)=800 across 2
        // holdings
        // pro-rata 50/50 split: 400 from each holding
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "40.00"),
                                target(Asset.AssetType.CRYPTO, "60.00")));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset bbb = stockAsset(stockAssetB, "BBB", new BigDecimal("200"));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("10")),
                                holding(stockAssetB, new BigDecimal("5")),
                                holding(cryptoAsset, new BigDecimal("10"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa, bbb, btc));

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        List<RebalanceSuggestion> sells =
                preview.suggestions().stream()
                        .filter(s -> s.action() == InvestmentTransaction.TxnType.SELL)
                        .toList();
        assertThat(sells).hasSize(2);
        assertThat(sells)
                .allSatisfy(s -> assertThat(s.assetType()).isEqualTo(Asset.AssetType.STOCK));
        // BUY suggestion concentrated on BTC
        List<RebalanceSuggestion> buys =
                preview.suggestions().stream()
                        .filter(s -> s.action() == InvestmentTransaction.TxnType.BUY)
                        .toList();
        assertThat(buys).hasSize(1);
        assertThat(buys.get(0).assetId()).isEqualTo(cryptoAsset);
    }

    @Test
    void preview_concentratesBuyOnHighestValueHolding() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "60.00"),
                                target(Asset.AssetType.CRYPTO, "40.00")));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset bbb = stockAsset(stockAssetB, "BBB", new BigDecimal("100"));
        // total stock value: AAA 100 + BBB 1000 = 1100, total 1100+1000 crypto=2100
        // stock at 1100/2100=52.4%, target 60% -> underweight by ~7.6pp
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("1")),
                                holding(stockAssetB, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("10"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa, bbb, btc));

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        // Single BUY on BBB (higher value). Crypto is overweight -> pro-rata SELL on BTC.
        List<RebalanceSuggestion> buys =
                preview.suggestions().stream()
                        .filter(s -> s.action() == InvestmentTransaction.TxnType.BUY)
                        .toList();
        assertThat(buys).hasSize(1);
        assertThat(buys.get(0).assetId()).isEqualTo(stockAssetB);
    }

    @Test
    void preview_emitsNoHoldingToBuyWarning_whenBucketEmpty() {
        // Only holds STOCK. Target list includes GOLD with 30% but zero gold holdings.
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "70.00"),
                                target(Asset.AssetType.GOLD, "30.00")));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(holding(stockAssetA, new BigDecimal("10"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa));

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        assertThat(preview.suggestions()).anyMatch(s -> "NO_HOLDING_TO_BUY".equals(s.warning()));
    }

    @Test
    void preview_proportionallyScalesBuyWhenCashShort() {
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(accountId, userId))
                .thenReturn(
                        Optional.of(
                                Account.builder()
                                        .id(accountId)
                                        .userId(userId)
                                        .currentBalance(new BigDecimal("100"))
                                        .build()));
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.CRYPTO, "70.00"),
                                target(Asset.AssetType.STOCK, "30.00")));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        // current: stock 1000 (10*100), crypto 100 (1*100). Total 1100. Stock=90.9%, Crypto=9.1%.
        // Crypto underweight ~ +60.9pp, target value 770 for crypto -> need ~670 BUY
        // Available cash 100, so scale-down to 100/670 ~= 0.149 -> only ~1 BTC fractional
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("1"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(btc, aaa));

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        assertThat(preview.summaryWarnings()).contains("CASH_PARTIAL_SCALEDOWN");
    }

    @Test
    void preview_truncatesStockQuantityToInteger() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "60.00"),
                                target(Asset.AssetType.CRYPTO, "40.00")));
        // Stock at 50% / target 60% -> underweight, BUY on stockA priced 100 with fractional result
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("5")),
                                holding(cryptoAsset, new BigDecimal("5"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa, btc));

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        preview.suggestions().stream()
                .filter(s -> s.assetType() == Asset.AssetType.STOCK)
                .forEach(s -> assertThat(s.quantity().scale()).isLessThanOrEqualTo(0));
    }

    @Test
    void preview_truncatesFundQuantityToInteger() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.FUND, "60.00"),
                                target(Asset.AssetType.CRYPTO, "40.00")));
        Asset fund = fundAsset(fundAsset, "FUND", new BigDecimal("33"));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(fundAsset, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("10"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(fund, btc));

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        preview.suggestions().stream()
                .filter(s -> s.assetType() == Asset.AssetType.FUND)
                .forEach(s -> assertThat(s.quantity().scale()).isLessThanOrEqualTo(0));
    }

    @Test
    void preview_keepsCryptoFractionalQuantity() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "30.00"),
                                target(Asset.AssetType.CRYPTO, "70.00")));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("60000"));
        // currently 50/50 by value: stock 1000 (10*100), crypto 1000 (60000*0.0167)
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("0.01666667"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa, btc));

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        preview.suggestions().stream()
                .filter(s -> s.assetType() == Asset.AssetType.CRYPTO)
                .forEach(s -> assertThat(s.quantity().scale()).isGreaterThan(0));
    }

    @Test
    void preview_storesProposalInRedisAndReturnsId() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(target(Asset.AssetType.STOCK, "100.00")));
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(assetRepository.findAllById(any())).thenReturn(List.of());

        RebalancePreview preview =
                service.preview(userId, portfolioId, new RebalancePreviewRequest(accountId, null));

        assertThat(preview.proposalId()).isNotNull();
        verify(proposalStore).putProposal(eq(userId), eq(preview.proposalId()), anyString());
    }

    @Test
    void preview_throwsAccountNotOwnedWhenAccountArchived() {
        when(accountRepository.findByIdAndUserIdAndArchivedFalse(accountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.preview(
                                        userId,
                                        portfolioId,
                                        new RebalancePreviewRequest(accountId, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("ACCOUNT_NOT_OWNED");
    }

    @Test
    void preview_throwsPortfolioNotFoundWhenWrongUser() {
        when(portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.preview(
                                        userId,
                                        portfolioId,
                                        new RebalancePreviewRequest(accountId, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void commit_materialisesSelectedRowsViaInvestmentTransactionService() {
        // Prepare a preview state
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "40.00"),
                                target(Asset.AssetType.CRYPTO, "60.00")));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("3"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa, btc));

        UUID proposalId = UUID.randomUUID();
        when(proposalStore.isCommitted(userId, proposalId)).thenReturn(false);
        // Compute the live hash on the fly when commit is invoked
        when(proposalStore.getProposal(eq(userId), eq(proposalId)))
                .thenAnswer(
                        invocation -> {
                            // Run a live preview to capture the hash that commit will recompute.
                            RebalancePreview live =
                                    service.preview(
                                            userId,
                                            portfolioId,
                                            new RebalancePreviewRequest(accountId, null));
                            return Optional.of(
                                    service.canonicalHash(
                                            portfolioId, accountId, live.suggestions()));
                        });

        when(transactionService.record(
                        eq(userId), eq(portfolioId), any(RecordTransactionRequest.class)))
                .thenAnswer(
                        inv -> {
                            return new TransactionResponse(
                                    UUID.randomUUID(),
                                    portfolioId,
                                    UUID.randomUUID(),
                                    "X",
                                    "X",
                                    InvestmentTransaction.TxnType.SELL,
                                    new BigDecimal("1"),
                                    new BigDecimal("100"),
                                    new BigDecimal("100"),
                                    BigDecimal.ZERO,
                                    null,
                                    LocalDate.now(),
                                    Instant.now(),
                                    null,
                                    null);
                        });

        RebalanceCommitRequest commitRequest =
                new RebalanceCommitRequest(proposalId, accountId, List.of(0));
        RebalanceCommitResult result = service.commit(userId, portfolioId, commitRequest);

        assertThat(result.committedCount()).isEqualTo(1);
        verify(transactionService, times(1))
                .record(eq(userId), eq(portfolioId), any(RecordTransactionRequest.class));
        verify(proposalStore).markCommitted(userId, proposalId);
    }

    @Test
    void commit_skipsUntickedRows() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "40.00"),
                                target(Asset.AssetType.CRYPTO, "60.00")));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset bbb = stockAsset(stockAssetB, "BBB", new BigDecimal("100"));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("10")),
                                holding(stockAssetB, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("3"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa, bbb, btc));

        UUID proposalId = UUID.randomUUID();
        when(proposalStore.isCommitted(userId, proposalId)).thenReturn(false);
        when(proposalStore.getProposal(eq(userId), eq(proposalId)))
                .thenAnswer(
                        invocation -> {
                            RebalancePreview live =
                                    service.preview(
                                            userId,
                                            portfolioId,
                                            new RebalancePreviewRequest(accountId, null));
                            return Optional.of(
                                    service.canonicalHash(
                                            portfolioId, accountId, live.suggestions()));
                        });
        when(transactionService.record(
                        eq(userId), eq(portfolioId), any(RecordTransactionRequest.class)))
                .thenAnswer(
                        inv ->
                                new TransactionResponse(
                                        UUID.randomUUID(),
                                        portfolioId,
                                        UUID.randomUUID(),
                                        "X",
                                        "X",
                                        InvestmentTransaction.TxnType.SELL,
                                        new BigDecimal("1"),
                                        new BigDecimal("100"),
                                        new BigDecimal("100"),
                                        BigDecimal.ZERO,
                                        null,
                                        LocalDate.now(),
                                        Instant.now(),
                                        null,
                                        null));

        RebalanceCommitRequest commitRequest =
                new RebalanceCommitRequest(proposalId, accountId, List.of(0));
        RebalanceCommitResult result = service.commit(userId, portfolioId, commitRequest);

        assertThat(result.committedCount()).isEqualTo(1);
        verify(transactionService, times(1))
                .record(eq(userId), eq(portfolioId), any(RecordTransactionRequest.class));
    }

    @Test
    void commit_throwsProposalNotFoundWhenRedisExpired() {
        UUID proposalId = UUID.randomUUID();
        when(proposalStore.isCommitted(userId, proposalId)).thenReturn(false);
        when(proposalStore.getProposal(userId, proposalId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.commit(
                                        userId,
                                        portfolioId,
                                        new RebalanceCommitRequest(
                                                proposalId, accountId, List.of(0))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionService, never())
                .record(any(), any(), any(RecordTransactionRequest.class));
    }

    @Test
    void commit_throwsProposalStaleWhenHashDiffers() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(target(Asset.AssetType.STOCK, "100.00")));
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(assetRepository.findAllById(any())).thenReturn(List.of());

        UUID proposalId = UUID.randomUUID();
        when(proposalStore.isCommitted(userId, proposalId)).thenReturn(false);
        when(proposalStore.getProposal(userId, proposalId)).thenReturn(Optional.of("00deadbeef"));

        assertThatThrownBy(
                        () ->
                                service.commit(
                                        userId,
                                        portfolioId,
                                        new RebalanceCommitRequest(
                                                proposalId, accountId, List.of())))
                .isInstanceOf(RebalanceConflictException.class)
                .extracting("code")
                .isEqualTo("REBALANCE_PROPOSAL_STALE");
    }

    @Test
    void commit_throwsAlreadyCommittedOnReplay() {
        UUID proposalId = UUID.randomUUID();
        when(proposalStore.isCommitted(userId, proposalId)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.commit(
                                        userId,
                                        portfolioId,
                                        new RebalanceCommitRequest(
                                                proposalId, accountId, List.of(0))))
                .isInstanceOf(RebalanceConflictException.class)
                .extracting("code")
                .isEqualTo("REBALANCE_PROPOSAL_ALREADY_COMMITTED");
    }

    @Test
    void commit_throwsWhenSelectionsOutOfRange() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "40.00"),
                                target(Asset.AssetType.CRYPTO, "60.00")));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("3"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa, btc));

        UUID proposalId = UUID.randomUUID();
        when(proposalStore.isCommitted(userId, proposalId)).thenReturn(false);
        when(proposalStore.getProposal(eq(userId), eq(proposalId)))
                .thenAnswer(
                        invocation -> {
                            RebalancePreview live =
                                    service.preview(
                                            userId,
                                            portfolioId,
                                            new RebalancePreviewRequest(accountId, null));
                            return Optional.of(
                                    service.canonicalHash(
                                            portfolioId, accountId, live.suggestions()));
                        });

        assertThatThrownBy(
                        () ->
                                service.commit(
                                        userId,
                                        portfolioId,
                                        new RebalanceCommitRequest(
                                                proposalId, accountId, List.of(99))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("REBALANCE_SELECTION_OUT_OF_RANGE");
    }

    @Test
    void commit_emitsAuditOnSuccess() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                target(Asset.AssetType.STOCK, "40.00"),
                                target(Asset.AssetType.CRYPTO, "60.00")));
        Asset aaa = stockAsset(stockAssetA, "AAA", new BigDecimal("100"));
        Asset btc = cryptoAsset(cryptoAsset, "BTC", new BigDecimal("100"));
        when(holdingRepository.findByPortfolioId(portfolioId))
                .thenReturn(
                        List.of(
                                holding(stockAssetA, new BigDecimal("10")),
                                holding(cryptoAsset, new BigDecimal("3"))));
        when(assetRepository.findAllById(any())).thenReturn(List.of(aaa, btc));

        UUID proposalId = UUID.randomUUID();
        when(proposalStore.isCommitted(userId, proposalId)).thenReturn(false);
        when(proposalStore.getProposal(eq(userId), eq(proposalId)))
                .thenAnswer(
                        invocation -> {
                            RebalancePreview live =
                                    service.preview(
                                            userId,
                                            portfolioId,
                                            new RebalancePreviewRequest(accountId, null));
                            return Optional.of(
                                    service.canonicalHash(
                                            portfolioId, accountId, live.suggestions()));
                        });
        when(transactionService.record(
                        eq(userId), eq(portfolioId), any(RecordTransactionRequest.class)))
                .thenAnswer(
                        inv ->
                                new TransactionResponse(
                                        UUID.randomUUID(),
                                        portfolioId,
                                        UUID.randomUUID(),
                                        "X",
                                        "X",
                                        InvestmentTransaction.TxnType.SELL,
                                        new BigDecimal("1"),
                                        new BigDecimal("100"),
                                        new BigDecimal("100"),
                                        BigDecimal.ZERO,
                                        null,
                                        LocalDate.now(),
                                        Instant.now(),
                                        null,
                                        null));

        service.commit(
                userId, portfolioId, new RebalanceCommitRequest(proposalId, accountId, List.of(0)));

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(auditService, org.mockito.Mockito.atLeastOnce())
                .success(action.capture(), eq(userId), any(), any());
        assertThat(action.getAllValues()).contains(AuditAction.REBALANCE_COMMITTED);
    }

    @Test
    void commit_emitsAuditFailureOnStaleProposal() {
        when(targetRepository.findByPortfolioId(portfolioId))
                .thenReturn(List.of(target(Asset.AssetType.STOCK, "100.00")));
        when(holdingRepository.findByPortfolioId(portfolioId)).thenReturn(List.of());
        when(assetRepository.findAllById(any())).thenReturn(List.of());

        UUID proposalId = UUID.randomUUID();
        when(proposalStore.isCommitted(userId, proposalId)).thenReturn(false);
        when(proposalStore.getProposal(userId, proposalId)).thenReturn(Optional.of("ffff"));

        assertThatThrownBy(
                        () ->
                                service.commit(
                                        userId,
                                        portfolioId,
                                        new RebalanceCommitRequest(
                                                proposalId, accountId, List.of())))
                .isInstanceOf(RebalanceConflictException.class);
        verify(auditService)
                .failure(eq(AuditAction.REBALANCE_COMMITTED), eq(userId), any(), anyString());
    }

    @Test
    void canonicalHash_isDeterministicAcrossRuns() {
        List<RebalanceSuggestion> suggestions =
                List.of(
                        new RebalanceSuggestion(
                                0,
                                stockAssetA,
                                "AAA",
                                Asset.AssetType.STOCK,
                                InvestmentTransaction.TxnType.SELL,
                                new BigDecimal("3"),
                                new BigDecimal("100.0000"),
                                new BigDecimal("300.0000"),
                                new BigDecimal("1000.0000"),
                                new BigDecimal("66.67"),
                                new BigDecimal("40.00"),
                                new BigDecimal("26.67"),
                                null));
        String h1 = service.canonicalHash(portfolioId, accountId, suggestions);
        String h2 = service.canonicalHash(portfolioId, accountId, suggestions);
        assertThat(h1).isEqualTo(h2).hasSize(64);
    }

    @Test
    void canonicalHash_differsWhenAccountIdChanges() {
        List<RebalanceSuggestion> suggestions =
                List.of(
                        new RebalanceSuggestion(
                                0,
                                stockAssetA,
                                "AAA",
                                Asset.AssetType.STOCK,
                                InvestmentTransaction.TxnType.SELL,
                                new BigDecimal("3"),
                                new BigDecimal("100.0000"),
                                new BigDecimal("300.0000"),
                                new BigDecimal("1000.0000"),
                                new BigDecimal("66.67"),
                                new BigDecimal("40.00"),
                                new BigDecimal("26.67"),
                                null));
        String h1 = service.canonicalHash(portfolioId, accountId, suggestions);
        String h2 = service.canonicalHash(portfolioId, UUID.randomUUID(), suggestions);
        assertThat(h1).isNotEqualTo(h2);
    }
}
