package com.fintrack.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published after a BillPayment row commits via {@code BillService.pay(...)}. Subscribers run via
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 */
public record BillPaidEvent(
        UUID userId,
        UUID billId,
        String billName,
        String period,
        BigDecimal amount,
        String currency,
        Instant paidAt) {}
