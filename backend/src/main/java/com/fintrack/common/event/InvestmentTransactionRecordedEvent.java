package com.fintrack.common.event;

import com.fintrack.common.entity.InvestmentTransaction;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published after the transaction row commits via {@code InvestmentTransactionService.record(...)}.
 * Subscribers run via {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 *
 * <p>{@code accountId} is the linked cash/bank account at commit time. {@code previousAccountId}
 * supports a future in-place edit path; create flows pass {@code null}.
 */
public record InvestmentTransactionRecordedEvent(
        UUID userId,
        UUID portfolioId,
        UUID assetId,
        UUID transactionId,
        InvestmentTransaction.TxnType txnType,
        BigDecimal quantity,
        BigDecimal priceTry,
        BigDecimal feeTry,
        UUID accountId,
        UUID previousAccountId) {}
