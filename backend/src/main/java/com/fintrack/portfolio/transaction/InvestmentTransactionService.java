package com.fintrack.portfolio.transaction;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.InvestmentTransaction;
import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.transaction.dto.RecordTransactionRequest;
import com.fintrack.portfolio.transaction.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentTransactionService {

    private final InvestmentTransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final AssetRepository assetRepository;

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(UUID userId, UUID portfolioId) {
        requireOwnedPortfolio(userId, portfolioId);

        List<InvestmentTransaction> txns = transactionRepository
                .findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(portfolioId);
        if (txns.isEmpty()) {
            return List.of();
        }

        Set<UUID> assetIds = txns.stream()
                .map(InvestmentTransaction::getAssetId)
                .collect(Collectors.toSet());
        Map<UUID, Asset> assetsById = new HashMap<>();
        assetRepository.findAllById(assetIds).forEach(a -> assetsById.put(a.getId(), a));

        return txns.stream()
                .map(t -> TransactionResponse.from(t, assetsById.get(t.getAssetId())))
                .toList();
    }

    @Transactional
    public TransactionResponse record(UUID userId, UUID portfolioId, RecordTransactionRequest request) {
        requireOwnedPortfolio(userId, portfolioId);

        Asset asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));

        BigDecimal fee = request.feeTry() != null ? request.feeTry() : BigDecimal.ZERO;
        BigDecimal amount = request.priceTry().multiply(request.quantity());
        if (request.txnType() == TxnType.BUY) {
            amount = amount.add(fee);
        } else if (request.txnType() == TxnType.SELL) {
            amount = amount.subtract(fee);
        }

        InvestmentTransaction txn = InvestmentTransaction.builder()
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

        applyToHolding(portfolioId, request, fee);

        log.info("Transaction recorded: id={} portfolioId={} assetId={} type={} qty={}",
                txn.getId(), portfolioId, request.assetId(), request.txnType(), request.quantity());

        return TransactionResponse.from(txn, asset);
    }

    @Transactional
    public void delete(UUID userId, UUID portfolioId, UUID txnId) {
        requireOwnedPortfolio(userId, portfolioId);
        InvestmentTransaction txn = transactionRepository.findByIdAndPortfolioId(txnId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        transactionRepository.delete(txn);
        log.info("Transaction deleted: id={} portfolioId={}", txnId, portfolioId);
    }

    private void applyToHolding(UUID portfolioId, RecordTransactionRequest request, BigDecimal fee) {
        TxnType type = request.txnType();
        if (type != TxnType.BUY && type != TxnType.SELL && type != TxnType.BES_CONTRIBUTION) {
            return;
        }

        PortfolioHolding holding = holdingRepository
                .findByPortfolioIdAndAssetId(portfolioId, request.assetId())
                .orElse(null);

        if (type == TxnType.BUY || type == TxnType.BES_CONTRIBUTION) {
            BigDecimal addedQty = request.quantity();
            BigDecimal addedCost = request.priceTry().multiply(addedQty).add(fee);

            if (holding == null) {
                BigDecimal avgCost = addedQty.signum() > 0
                        ? addedCost.divide(addedQty, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                holding = PortfolioHolding.builder()
                        .portfolioId(portfolioId)
                        .assetId(request.assetId())
                        .quantity(addedQty)
                        .avgCostTry(avgCost)
                        .build();
            } else {
                BigDecimal oldQty = holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
                BigDecimal oldAvg = holding.getAvgCostTry() != null ? holding.getAvgCostTry() : BigDecimal.ZERO;
                BigDecimal oldCost = oldQty.multiply(oldAvg);
                BigDecimal newQty = oldQty.add(addedQty);
                BigDecimal newAvg = newQty.signum() > 0
                        ? oldCost.add(addedCost).divide(newQty, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                holding.setQuantity(newQty);
                holding.setAvgCostTry(newAvg);
            }
            holdingRepository.save(holding);
            return;
        }

        if (holding == null) {
            throw new BusinessRuleException(
                    "Cannot sell an asset that is not in the portfolio",
                    "HOLDING_NOT_FOUND");
        }
        BigDecimal oldQty = holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = oldQty.subtract(request.quantity());
        if (newQty.signum() < 0) {
            throw new BusinessRuleException(
                    "Sell quantity exceeds current holding",
                    "HOLDING_INSUFFICIENT");
        }
        if (newQty.signum() == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(newQty);
            holdingRepository.save(holding);
        }
    }

    private Portfolio requireOwnedPortfolio(UUID userId, UUID portfolioId) {
        return portfolioRepository.findByIdAndUserIdAndActiveTrue(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }
}
