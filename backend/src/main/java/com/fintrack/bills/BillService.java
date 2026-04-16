package com.fintrack.bills;

import com.fintrack.bills.dto.*;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillService {

    private final BillRepository billRepo;
    private final BillPaymentRepository paymentRepo;

    @Transactional(readOnly = true)
    public List<BillResponse> listForUser(UUID userId) {
        String currentPeriod = currentPeriod();
        return billRepo.findByUserIdOrderByDueDayAsc(userId).stream()
                .map(bill -> {
                    BillPayment payment = paymentRepo.findByBillIdAndPeriod(bill.getId(), currentPeriod)
                            .orElse(null);
                    return BillResponse.from(bill, payment, computeVariance(bill.getId()));
                })
                .toList();
    }

    @Transactional
    public BillResponse create(UUID userId, CreateBillRequest req) {
        Bill bill = Bill.builder()
                .userId(userId)
                .name(req.name())
                .amount(req.amount())
                .dueDay(req.dueDay())
                .category(req.category())
                .remindDaysBefore(req.remindDaysBefore())
                .autoPay(req.autoPay())
                .notes(req.notes())
                .build();
        bill = billRepo.save(bill);
        log.info("Bill created: id={} name={} dueDay={}", bill.getId(), bill.getName(), bill.getDueDay());
        return BillResponse.from(bill, null);
    }

    @Transactional
    public BillResponse update(UUID userId, UUID billId, CreateBillRequest req) {
        Bill bill = requireOwned(userId, billId);
        bill.setName(req.name());
        bill.setAmount(req.amount());
        bill.setDueDay(req.dueDay());
        bill.setCategory(req.category());
        bill.setRemindDaysBefore(req.remindDaysBefore());
        bill.setAutoPay(req.autoPay());
        bill.setNotes(req.notes());

        String currentPeriod = currentPeriod();
        BillPayment payment = paymentRepo.findByBillIdAndPeriod(billId, currentPeriod).orElse(null);
        return BillResponse.from(bill, payment);
    }

    @Transactional
    public void delete(UUID userId, UUID billId) {
        Bill bill = requireOwned(userId, billId);
        billRepo.delete(bill);
        log.info("Bill deleted: id={}", billId);
    }

    @Transactional
    public BillResponse pay(UUID userId, UUID billId, PayBillRequest req) {
        Bill bill = requireOwned(userId, billId);

        BillPayment payment = paymentRepo.findByBillIdAndPeriod(billId, req.period())
                .orElse(BillPayment.builder()
                        .billId(billId)
                        .period(req.period())
                        .amount(req.amount() != null ? req.amount() : bill.getAmount())
                        .build());
        payment.setStatus(BillPayment.PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setAmount(req.amount() != null ? req.amount() : bill.getAmount());
        if (req.notes() != null) {
            payment.setNotes(req.notes());
        }
        paymentRepo.save(payment);
        log.info("Bill paid: billId={} period={} amount={}", billId, req.period(), payment.getAmount());
        return BillResponse.from(bill, payment, computeVariance(bill.getId()));
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> history(UUID userId, UUID billId) {
        requireOwned(userId, billId);
        return paymentRepo.findByBillIdOrderByPeriodDesc(billId).stream()
                .map(PaymentHistoryResponse::from)
                .toList();
    }

    private Bill requireOwned(UUID userId, UUID billId) {
        return billRepo.findByIdAndUserId(billId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
    }

    private String currentPeriod() {
        LocalDate today = LocalDate.now();
        return today.getYear() + "-" + String.format("%02d", today.getMonthValue());
    }

    /** Flag variance when the latest paid period differs from the prior one by more than 10%% or 25 units. */
    private static final BigDecimal FLAG_PERCENT = BigDecimal.valueOf(10);
    private static final BigDecimal FLAG_ABSOLUTE = BigDecimal.valueOf(25);

    private BillVarianceDto computeVariance(UUID billId) {
        List<BillPayment> latest = paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(
                billId, BillPayment.PaymentStatus.PAID);
        if (latest.size() < 2) return null;

        BillPayment current = latest.get(0);
        BillPayment previous = latest.get(1);
        BigDecimal delta = current.getAmount().subtract(previous.getAmount());
        BigDecimal deltaPercent = previous.getAmount().signum() == 0
                ? BigDecimal.ZERO
                : delta.multiply(BigDecimal.valueOf(100))
                        .divide(previous.getAmount(), 2, RoundingMode.HALF_UP);

        boolean flagged = delta.abs().compareTo(FLAG_ABSOLUTE) > 0
                && deltaPercent.abs().compareTo(FLAG_PERCENT) > 0;

        return new BillVarianceDto(
                current.getPeriod(),
                current.getAmount(),
                previous.getPeriod(),
                previous.getAmount(),
                delta,
                deltaPercent,
                flagged
        );
    }
}
