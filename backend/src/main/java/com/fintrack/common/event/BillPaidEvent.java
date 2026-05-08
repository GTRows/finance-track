package com.fintrack.common.event;

import com.fintrack.common.entity.BillPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published after a BillPayment row commits via {@code BillService.pay(...)}. Subscribers run via
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 *
 * <p>{@code accountId} is the linked account at commit time. {@code previousStatus} + {@code
 * previousAmount} let the AccountBalanceListener compute correct deltas across status transitions
 * (PENDING -&gt; PAID, PAID -&gt; PENDING, PAID -&gt; SKIPPED, etc.). {@code previousAccountId}
 * carries the prior account so the listener can reverse the delta on the previous account when the
 * operator switches accounts mid-transition.
 */
public record BillPaidEvent(
        UUID userId,
        UUID billId,
        String billName,
        String period,
        BigDecimal amount,
        String currency,
        Instant paidAt,
        UUID accountId,
        BillPayment.PaymentStatus previousStatus,
        BigDecimal previousAmount,
        UUID previousAccountId) {}
