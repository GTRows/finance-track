package com.fintrack.portfolio.holding;

import com.fintrack.common.entity.InvestmentTransaction.TxnType;
import com.fintrack.common.entity.PortfolioHolding;
import com.fintrack.common.event.InvestmentTransactionDeletedEvent;
import com.fintrack.common.event.InvestmentTransactionRecordedEvent;
import com.fintrack.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Projects {@link InvestmentTransactionRecordedEvent} payloads onto the {@code portfolio_holdings}
 * table. Runs after the writer's transaction commits, so a failure here cannot roll the writer
 * back; the SELL guard inside {@code InvestmentTransactionService} pre-validates the holding state
 * before the event is published.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldingProjectionListener {

    private final HoldingRepository holdingRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionRecorded(InvestmentTransactionRecordedEvent event) {
        try {
            apply(event);
        } catch (BusinessRuleException e) {
            log.error(
                    "Holding projection failed for txn={}: {}",
                    event.transactionId(),
                    e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionDeleted(InvestmentTransactionDeletedEvent event) {
        log.debug("Transaction deleted: {} (no holding rollback wired)", event.transactionId());
    }

    private void apply(InvestmentTransactionRecordedEvent event) {
        TxnType type = event.txnType();
        if (type != TxnType.BUY && type != TxnType.SELL && type != TxnType.BES_CONTRIBUTION) {
            return;
        }

        PortfolioHolding holding =
                holdingRepository
                        .findByPortfolioIdAndAssetId(event.portfolioId(), event.assetId())
                        .orElse(null);

        BigDecimal fee = event.feeTry() != null ? event.feeTry() : BigDecimal.ZERO;

        if (type == TxnType.BUY || type == TxnType.BES_CONTRIBUTION) {
            BigDecimal addedQty = event.quantity();
            BigDecimal addedCost = event.priceTry().multiply(addedQty).add(fee);

            if (holding == null) {
                BigDecimal avgCost =
                        addedQty.signum() > 0
                                ? addedCost.divide(addedQty, 4, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                holding =
                        PortfolioHolding.builder()
                                .portfolioId(event.portfolioId())
                                .assetId(event.assetId())
                                .quantity(addedQty)
                                .avgCostTry(avgCost)
                                .build();
            } else {
                BigDecimal oldQty =
                        holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
                BigDecimal oldAvg =
                        holding.getAvgCostTry() != null ? holding.getAvgCostTry() : BigDecimal.ZERO;
                BigDecimal oldCost = oldQty.multiply(oldAvg);
                BigDecimal newQty = oldQty.add(addedQty);
                BigDecimal newAvg =
                        newQty.signum() > 0
                                ? oldCost.add(addedCost).divide(newQty, 4, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                holding.setQuantity(newQty);
                holding.setAvgCostTry(newAvg);
            }
            holdingRepository.save(holding);
            return;
        }

        if (holding == null) {
            log.warn(
                    "SELL projection found no holding for txn={} portfolioId={} assetId={};"
                            + " writer-side guard should have rejected before publish",
                    event.transactionId(),
                    event.portfolioId(),
                    event.assetId());
            return;
        }
        BigDecimal oldQty = holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = oldQty.subtract(event.quantity());
        if (newQty.signum() < 0) {
            log.warn(
                    "SELL projection would go negative for txn={} portfolioId={} assetId={};"
                            + " writer-side guard should have rejected before publish",
                    event.transactionId(),
                    event.portfolioId(),
                    event.assetId());
            return;
        }
        if (newQty.signum() == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(newQty);
            holdingRepository.save(holding);
        }
    }
}
