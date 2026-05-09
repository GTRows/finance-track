package com.fintrack.bills;

import com.fintrack.account.AccountRepository;
import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.bills.dto.*;
import com.fintrack.common.entity.Account;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.event.BillPaidEvent;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
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
public class BillService {

    private final BillRepository billRepo;
    private final BillPaymentRepository paymentRepo;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<BillResponse> listForUser(UUID userId) {
        String currentPeriod = currentPeriod();
        List<Bill> bills = billRepo.findByUserIdOrderByDueDayAsc(userId);
        if (bills.isEmpty()) return List.of();

        List<UUID> billIds = bills.stream().map(Bill::getId).toList();
        Map<UUID, BillPayment> paymentByBillId =
                paymentRepo.findByBillIdInAndPeriod(billIds, currentPeriod).stream()
                        .collect(Collectors.toMap(BillPayment::getBillId, p -> p, (a, b) -> a));
        Map<UUID, List<BillPayment>> paidPaymentsByBillId =
                paymentRepo
                        .findByBillIdInAndStatusOrderByPeriodDesc(
                                billIds, BillPayment.PaymentStatus.PAID)
                        .stream()
                        .collect(Collectors.groupingBy(BillPayment::getBillId));

        Set<UUID> accountIds = new HashSet<>();
        for (BillPayment payment : paymentByBillId.values()) {
            if (payment.getAccountId() != null) {
                accountIds.add(payment.getAccountId());
            }
        }
        Map<UUID, String> accountNameById = new HashMap<>();
        if (!accountIds.isEmpty()) {
            for (Account a : accountRepository.findAllById(accountIds)) {
                accountNameById.put(a.getId(), a.getName());
            }
        }
        return bills.stream()
                .map(
                        bill -> {
                            BillPayment payment = paymentByBillId.get(bill.getId());
                            String accountName =
                                    payment != null && payment.getAccountId() != null
                                            ? accountNameById.get(payment.getAccountId())
                                            : null;
                            List<BillPayment> paidRows =
                                    paidPaymentsByBillId.getOrDefault(bill.getId(), List.of());
                            BillVarianceDto variance =
                                    varianceFromTopTwo(
                                            paidRows.size() <= 2
                                                    ? paidRows
                                                    : paidRows.subList(0, 2));
                            return BillResponse.from(bill, payment, variance, accountName);
                        })
                .toList();
    }

    @Transactional
    public BillResponse create(UUID userId, CreateBillRequest req) {
        Bill bill =
                Bill.builder()
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
        log.info(
                "Bill created: id={} name={} dueDay={}",
                bill.getId(),
                bill.getName(),
                bill.getDueDay());
        auditService.success(
                AuditAction.BILL_CREATED, userId, currentUsername(), "id=" + bill.getId());
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
        auditService.success(AuditAction.BILL_UPDATED, userId, currentUsername(), "id=" + billId);
        return BillResponse.from(bill, payment);
    }

    @Transactional
    public void delete(UUID userId, UUID billId) {
        Bill bill = requireOwned(userId, billId);
        billRepo.delete(bill);
        log.info("Bill deleted: id={}", billId);
        auditService.success(AuditAction.BILL_DELETED, userId, currentUsername(), "id=" + billId);
    }

    @Transactional
    public BillResponse pay(UUID userId, UUID billId, PayBillRequest req) {
        Bill bill = requireOwned(userId, billId);
        UUID accountId =
                resolveAccountOwnership(userId, req.accountId(), AuditAction.BILL_PAYMENT_RECORDED);

        BillPayment payment =
                paymentRepo
                        .findByBillIdAndPeriod(billId, req.period())
                        .orElse(
                                BillPayment.builder()
                                        .billId(billId)
                                        .period(req.period())
                                        .amount(
                                                req.amount() != null
                                                        ? req.amount()
                                                        : bill.getAmount())
                                        .build());
        BillPayment.PaymentStatus previousStatus = payment.getStatus();
        BigDecimal previousAmount =
                payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
        UUID previousAccountId = payment.getAccountId();

        payment.setStatus(BillPayment.PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setAmount(req.amount() != null ? req.amount() : bill.getAmount());
        payment.setAccountId(accountId);
        if (req.notes() != null) {
            payment.setNotes(req.notes());
        }
        paymentRepo.save(payment);
        log.info(
                "Bill paid: billId={} period={} amount={}",
                billId,
                req.period(),
                payment.getAmount());
        auditService.success(
                AuditAction.BILL_PAYMENT_RECORDED,
                userId,
                currentUsername(),
                "billId=" + billId + " period=" + req.period() + " accountId=" + accountId);
        eventPublisher.publishEvent(
                new BillPaidEvent(
                        userId,
                        billId,
                        bill.getName(),
                        payment.getPeriod(),
                        payment.getAmount(),
                        bill.getCurrency(),
                        payment.getPaidAt(),
                        payment.getAccountId(),
                        previousStatus,
                        previousAmount,
                        previousAccountId));
        String accountName =
                accountId != null
                        ? accountRepository.findById(accountId).map(Account::getName).orElse(null)
                        : null;
        return BillResponse.from(bill, payment, computeVariance(bill.getId()), accountName);
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> history(UUID userId, UUID billId) {
        requireOwned(userId, billId);
        return paymentRepo.findByBillIdOrderByPeriodDesc(billId).stream()
                .map(PaymentHistoryResponse::from)
                .toList();
    }

    @Transactional
    public BillResponse markUsed(UUID userId, UUID billId) {
        Bill bill = requireOwned(userId, billId);
        bill.setLastUsedOn(LocalDate.now());
        log.info("Bill marked as used: id={}", billId);

        BillPayment payment =
                paymentRepo.findByBillIdAndPeriod(billId, currentPeriod()).orElse(null);
        return BillResponse.from(bill, payment, computeVariance(bill.getId()));
    }

    private static final int STALE_DAYS_SINCE_USE = 90;
    private static final int MIN_AGE_FOR_AUDIT_DAYS = 60;

    @Transactional(readOnly = true)
    public SubscriptionAuditDto audit(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate staleCutoff = today.minusDays(STALE_DAYS_SINCE_USE);
        Instant ageCutoff =
                today.minusDays(MIN_AGE_FOR_AUDIT_DAYS)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant();

        List<Bill> active = billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId);
        BigDecimal total =
                active.stream().map(Bill::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SubscriptionAuditDto.Candidate> candidates =
                active.stream()
                        .filter(
                                b ->
                                        b.getCreatedAt() != null
                                                && b.getCreatedAt().isBefore(ageCutoff))
                        .map(b -> toCandidate(b, staleCutoff))
                        .filter(c -> c != null)
                        .toList();

        BigDecimal savings =
                candidates.stream()
                        .map(SubscriptionAuditDto.Candidate::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SubscriptionAuditDto(total, savings, candidates.size(), candidates);
    }

    private SubscriptionAuditDto.Candidate toCandidate(Bill bill, LocalDate staleCutoff) {
        LocalDate lastUsed = bill.getLastUsedOn();
        String reason;
        Long daysSince;
        if (lastUsed == null) {
            reason = "NEVER_USED";
            daysSince = null;
        } else if (lastUsed.isBefore(staleCutoff)) {
            reason = "STALE";
            daysSince = ChronoUnit.DAYS.between(lastUsed, LocalDate.now());
        } else {
            return null;
        }
        return new SubscriptionAuditDto.Candidate(
                bill.getId(),
                bill.getName(),
                bill.getCategory(),
                bill.getAmount(),
                bill.getCurrency(),
                lastUsed,
                daysSince,
                reason);
    }

    private UUID resolveAccountOwnership(UUID userId, UUID accountId, String failureAction) {
        if (accountId == null) return null;
        if (accountRepository.findByIdAndUserIdAndArchivedFalse(accountId, userId).isEmpty()) {
            auditService.failure(
                    failureAction, userId, currentUsername(), "accountId=" + accountId);
            throw new BusinessRuleException("Account not found", "ACCOUNT_NOT_OWNED");
        }
        return accountId;
    }

    private Bill requireOwned(UUID userId, UUID billId) {
        return billRepo.findByIdAndUserId(billId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private String currentPeriod() {
        LocalDate today = LocalDate.now();
        return today.getYear() + "-" + String.format("%02d", today.getMonthValue());
    }

    /**
     * Flag variance when the latest paid period differs from the prior one by more than 10%% or 25
     * units.
     */
    private static final BigDecimal FLAG_PERCENT = BigDecimal.valueOf(10);

    private static final BigDecimal FLAG_ABSOLUTE = BigDecimal.valueOf(25);

    private BillVarianceDto computeVariance(UUID billId) {
        List<BillPayment> latest =
                paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(
                        billId, BillPayment.PaymentStatus.PAID);
        return varianceFromTopTwo(latest);
    }

    /**
     * Builds a variance DTO from the latest two PAID payments for a bill (descending by period).
     * Mirrors {@link #computeVariance(UUID)} but accepts the top-two payments directly so callers
     * that already batched the lookup can avoid re-querying. Returns {@code null} when fewer than
     * two payments are supplied.
     */
    static BillVarianceDto varianceFromTopTwo(List<BillPayment> topTwo) {
        if (topTwo == null || topTwo.size() < 2) return null;

        BillPayment current = topTwo.get(0);
        BillPayment previous = topTwo.get(1);
        BigDecimal delta = current.getAmount().subtract(previous.getAmount());
        BigDecimal deltaPercent =
                previous.getAmount().signum() == 0
                        ? BigDecimal.ZERO
                        : delta.multiply(BigDecimal.valueOf(100))
                                .divide(previous.getAmount(), 2, RoundingMode.HALF_UP);

        boolean flagged =
                delta.abs().compareTo(FLAG_ABSOLUTE) > 0
                        && deltaPercent.abs().compareTo(FLAG_PERCENT) > 0;

        return new BillVarianceDto(
                current.getPeriod(),
                current.getAmount(),
                previous.getPeriod(),
                previous.getAmount(),
                delta,
                deltaPercent,
                flagged);
    }
}
