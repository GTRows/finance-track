package com.fintrack.portfolio.transaction;

import com.fintrack.asset.AssetRepository;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.event.InvestmentTransactionDeletedEvent;
import com.fintrack.common.event.InvestmentTransactionRecordedEvent;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.transaction.dto.RecordTransactionRequest;
import com.fintrack.portfolio.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentTransactionService {

    private final InvestmentTransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final AssetRepository assetRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(UUID userId, UUID portfolioId) {
        requireOwnedPortfolio(userId, portfolioId);

        List<InvestmentTransaction> txns =
                transactionRepository.findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(portfolioId);
        if (txns.isEmpty()) {
            return List.of();
        }

        Set<UUID> assetIds =
                txns.stream().map(InvestmentTransaction::getAssetId).collect(Collectors.toSet());
        Map<UUID, Asset> assetsById = new HashMap<>();
        assetRepository.findAllById(assetIds).forEach(a -> assetsById.put(a.getId(), a));

        return txns.stream()
                .map(t -> TransactionResponse.from(t, assetsById.get(t.getAssetId())))
                .toList();
    }

    @Transactional
    public TransactionResponse record(
            UUID userId, UUID portfolioId, RecordTransactionRequest request) {
        requireOwnedPortfolio(userId, portfolioId);

        Asset asset =
                assetRepository
                        .findById(request.assetId())
                        .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        BigDecimal fee = request.feeTry() != null ? request.feeTry() : BigDecimal.ZERO;
        BigDecimal amount = request.priceTry().multiply(request.quantity());
        if (request.txnType() == TxnType.BUY) {
            amount = amount.add(fee);
        } else if (request.txnType() == TxnType.SELL) {
            amount = amount.subtract(fee);
        }

        try {
            validateSellPossible(portfolioId, request);
        } catch (BusinessRuleException ex) {
            auditService.failure(
                    AuditAction.INVESTMENT_TRANSACTION_CREATED,
                    userId,
                    currentUsername(),
                    ex.getMessage());
            throw ex;
        }

        InvestmentTransaction txn =
                InvestmentTransaction.builder()
                        .portfolioId(portfolioId)
                        .assetId(request.assetId())
                        .txnType(request.txnType())
                        .quantity(request.quantity())
                        .priceTry(request.priceTry())
                        .amountTry(amount)
                        .feeTry(fee)
                        .notes(request.notes())
                        .txnDate(request.txnDate())
                        .build();
        txn = transactionRepository.save(txn);

        log.info(
                "Transaction recorded: id={} portfolioId={} assetId={} type={} qty={}",
                txn.getId(),
                portfolioId,
                request.assetId(),
                request.txnType(),
                request.quantity());
        auditService.success(
                AuditAction.INVESTMENT_TRANSACTION_CREATED,
                userId,
                currentUsername(),
                "id=" + txn.getId());

        eventPublisher.publishEvent(
                new InvestmentTransactionRecordedEvent(
                        userId,
                        portfolioId,
                        request.assetId(),
                        txn.getId(),
                        request.txnType(),
                        request.quantity(),
                        request.priceTry(),
                        fee,
                        txn.getAccountId(),
                        null));

        return TransactionResponse.from(txn, asset);
    }

    @Transactional
    public void delete(UUID userId, UUID portfolioId, UUID txnId) {
        requireOwnedPortfolio(userId, portfolioId);
        InvestmentTransaction txn =
                transactionRepository
                        .findByIdAndPortfolioId(txnId, portfolioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        UUID assetId = txn.getAssetId();
        TxnType txnType = txn.getTxnType();
        BigDecimal quantity = txn.getQuantity();
        BigDecimal priceTry = txn.getPriceTry();
        BigDecimal feeTry = txn.getFeeTry();
        UUID accountId = txn.getAccountId();

        transactionRepository.delete(txn);
        log.info("Transaction deleted: id={} portfolioId={}", txnId, portfolioId);
        auditService.success(
                AuditAction.INVESTMENT_TRANSACTION_DELETED,
                userId,
                currentUsername(),
                "id=" + txnId);

        eventPublisher.publishEvent(
                new InvestmentTransactionDeletedEvent(
                        userId,
                        portfolioId,
                        assetId,
                        txnId,
                        txnType,
                        quantity,
                        priceTry,
                        feeTry,
                        accountId));
    }

    /**
     * Pre-write guard for SELL transactions. The SELL flow used to roll back the writer's
     * transaction when the holding update failed; that contract is preserved by validating the
     * holding state before the event is published. BUY / BES_CONTRIBUTION need no pre-validation
     * because the listener can always create or extend the holding.
     */
    private void validateSellPossible(UUID portfolioId, RecordTransactionRequest request) {
        if (request.txnType() != TxnType.SELL) {
            return;
        }
        PortfolioHolding holding =
                holdingRepository
                        .findByPortfolioIdAndAssetId(portfolioId, request.assetId())
                        .orElse(null);
        if (holding == null) {
            throw new BusinessRuleException(
                    "Cannot sell an asset that is not in the portfolio", "HOLDING_NOT_FOUND");
        }
        BigDecimal oldQty = holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = oldQty.subtract(request.quantity());
        if (newQty.signum() < 0) {
            throw new BusinessRuleException(
                    "Sell quantity exceeds current holding", "HOLDING_INSUFFICIENT");
        }
    }

    private Portfolio requireOwnedPortfolio(UUID userId, UUID portfolioId) {
        return portfolioRepository
                .findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
