package com.fintrack.common.event;

import com.fintrack.common.entity.InvestmentTransaction;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published after the transaction row is removed via {@code
 * InvestmentTransactionService.delete(...)}. Subscribers run via
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public record InvestmentTransactionDeletedEvent(
        UUID userId,
        UUID portfolioId,
        UUID assetId,
        UUID transactionId,
        InvestmentTransaction.TxnType txnType,
        BigDecimal quantity,
        BigDecimal priceTry,
        BigDecimal feeTry,
        UUID accountId) {}
