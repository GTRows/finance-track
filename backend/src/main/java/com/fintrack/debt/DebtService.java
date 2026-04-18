package com.fintrack.debt;

import com.fintrack.common.entity.Debt;
import com.fintrack.common.entity.DebtPayment;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.debt.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtService {

    private static final MathContext MC = new MathContext(14, RoundingMode.HALF_UP);
    private static final int PREVIEW_ROWS = 6;

    private final DebtRepository debtRepo;
    private final DebtPaymentRepository paymentRepo;

    @Transactional(readOnly = true)
    public List<DebtResponse> list(UUID userId) {
        return debtRepo.findActive(userId).stream()
                .map(d -> toResponse(d, paymentRepo.sumByDebtId(d.getId())))
                .toList();
    }

    @Transactional
    public DebtResponse create(UUID userId, UpsertDebtRequest req) {
        Debt debt = Debt.builder()
                .userId(userId)
                .name(req.name())
                .debtType(req.debtType())
                .principal(req.principal())
                .annualRate(req.annualRate())
                .termMonths(req.termMonths())
                .startDate(req.startDate())
                .notes(req.notes())
                .build();
        debt = debtRepo.save(debt);
        log.info("Debt created: id={} name={}", debt.getId(), debt.getName());
        return toResponse(debt, BigDecimal.ZERO);
    }

    @Transactional
    public DebtResponse update(UUID userId, UUID id, UpsertDebtRequest req) {
        Debt debt = requireOwned(userId, id);
        debt.setName(req.name());
        debt.setDebtType(req.debtType());
        debt.setPrincipal(req.principal());
        debt.setAnnualRate(req.annualRate());
        debt.setTermMonths(req.termMonths());
        debt.setStartDate(req.startDate());
        debt.setNotes(req.notes());
        return toResponse(debt, paymentRepo.sumByDebtId(id));
    }

    @Transactional
    public void archive(UUID userId, UUID id) {
        Debt debt = requireOwned(userId, id);
        debt.setArchivedAt(Instant.now());
        log.info("Debt archived: id={}", id);
    }

    @Transactional(readOnly = true)
    public List<DebtPaymentResponse> listPayments(UUID userId, UUID debtId) {
        requireOwned(userId, debtId);
        return paymentRepo.findByDebtIdOrderByPaymentDateAsc(debtId).stream()
                .map(DebtPaymentResponse::from)
                .toList();
    }

    @Transactional
    public DebtPaymentResponse addPayment(UUID userId, UUID debtId, DebtPaymentRequest req) {
        requireOwned(userId, debtId);
        DebtPayment payment = DebtPayment.builder()
                .debtId(debtId)
                .paymentDate(req.paymentDate())
                .amount(req.amount())
                .note(req.note())
                .build();
        payment = paymentRepo.save(payment);
        return DebtPaymentResponse.from(payment);
    }

    @Transactional
    public void deletePayment(UUID userId, UUID debtId, UUID paymentId) {
        requireOwned(userId, debtId);
        DebtPayment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (!payment.getDebtId().equals(debtId)) {
            throw new ResourceNotFoundException("Payment not found");
        }
        paymentRepo.delete(payment);
    }

    private Debt requireOwned(UUID userId, UUID id) {
        return debtRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Debt not found"));
    }

    private DebtResponse toResponse(Debt debt, BigDecimal actuallyPaid) {
        BigDecimal monthly = scheduledMonthlyPayment(debt);
        BigDecimal totalScheduled = monthly.multiply(BigDecimal.valueOf(debt.getTermMonths()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = totalScheduled.subtract(debt.getPrincipal()).max(BigDecimal.ZERO);

        BigDecimal remaining = simulateRemaining(debt, actuallyPaid);
        BigDecimal progress = totalScheduled.signum() > 0
                ? actuallyPaid.divide(totalScheduled, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE)
                : BigDecimal.ZERO;

        LocalDate scheduledPayoff = debt.getStartDate().plusMonths(debt.getTermMonths());

        ProjectedPayoff projection = projectPayoff(debt, monthly, remaining);
        long monthsAhead = projection.payoff != null
                ? java.time.temporal.ChronoUnit.MONTHS.between(
                        projection.payoff.withDayOfMonth(1),
                        scheduledPayoff.withDayOfMonth(1))
                : 0;

        String status = remaining.signum() <= 0 ? "PAID_OFF" : "ACTIVE";

        List<DebtResponse.AmortizationRow> preview = buildPreview(debt, monthly, remaining);

        return new DebtResponse(
                debt.getId(),
                debt.getName(),
                debt.getDebtType(),
                debt.getPrincipal(),
                debt.getAnnualRate(),
                debt.getTermMonths(),
                debt.getStartDate(),
                debt.getNotes(),
                monthly,
                totalScheduled,
                actuallyPaid,
                remaining,
                totalInterest,
                scheduledPayoff,
                projection.payoff,
                progress,
                (int) (-monthsAhead),
                status,
                preview
        );
    }

    private BigDecimal scheduledMonthlyPayment(Debt debt) {
        BigDecimal principal = debt.getPrincipal();
        BigDecimal annualRate = debt.getAnnualRate();
        int n = debt.getTermMonths();

        if (annualRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }

        BigDecimal r = annualRate.divide(BigDecimal.valueOf(12), MC);
        BigDecimal onePlusR = BigDecimal.ONE.add(r);
        double pow = Math.pow(onePlusR.doubleValue(), -n);
        BigDecimal denom = BigDecimal.ONE.subtract(BigDecimal.valueOf(pow), MC);
        if (denom.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }
        return principal.multiply(r, MC).divide(denom, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal simulateRemaining(Debt debt, BigDecimal totalPaid) {
        if (totalPaid.signum() <= 0) return debt.getPrincipal();
        BigDecimal balance = debt.getPrincipal();
        BigDecimal r = debt.getAnnualRate().signum() == 0
                ? BigDecimal.ZERO
                : debt.getAnnualRate().divide(BigDecimal.valueOf(12), MC);
        BigDecimal monthly = scheduledMonthlyPayment(debt);

        BigDecimal pool = totalPaid;
        int safetyCap = debt.getTermMonths() + 240;
        while (pool.signum() > 0 && balance.signum() > 0 && safetyCap-- > 0) {
            BigDecimal installment = pool.min(monthly);
            BigDecimal interest = balance.multiply(r, MC);
            BigDecimal principalPart = installment.subtract(interest);
            if (principalPart.signum() <= 0) {
                pool = pool.subtract(installment);
                continue;
            }
            BigDecimal applied = principalPart.min(balance);
            balance = balance.subtract(applied);
            pool = pool.subtract(installment);
        }
        return balance.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private record ProjectedPayoff(LocalDate payoff) {}

    private ProjectedPayoff projectPayoff(Debt debt, BigDecimal monthly, BigDecimal remaining) {
        if (remaining.signum() <= 0) return new ProjectedPayoff(null);
        BigDecimal r = debt.getAnnualRate().signum() == 0
                ? BigDecimal.ZERO
                : debt.getAnnualRate().divide(BigDecimal.valueOf(12), MC);

        BigDecimal balance = remaining;
        int months = 0;
        while (balance.signum() > 0 && months < debt.getTermMonths() + 240) {
            BigDecimal interest = balance.multiply(r, MC);
            BigDecimal principalPart = monthly.subtract(interest);
            if (principalPart.signum() <= 0) return new ProjectedPayoff(null);
            balance = balance.subtract(principalPart);
            months++;
        }
        if (balance.signum() > 0) return new ProjectedPayoff(null);
        return new ProjectedPayoff(LocalDate.now().plusMonths(months));
    }

    private List<DebtResponse.AmortizationRow> buildPreview(Debt debt, BigDecimal monthly, BigDecimal remaining) {
        if (remaining.signum() <= 0) return List.of();
        BigDecimal r = debt.getAnnualRate().signum() == 0
                ? BigDecimal.ZERO
                : debt.getAnnualRate().divide(BigDecimal.valueOf(12), MC);

        List<DebtResponse.AmortizationRow> rows = new ArrayList<>();
        BigDecimal balance = remaining;
        LocalDate due = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        for (int i = 0; i < PREVIEW_ROWS && balance.signum() > 0; i++) {
            BigDecimal interest = balance.multiply(r, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal payment = balance.add(interest).min(monthly);
            BigDecimal principalPart = payment.subtract(interest);
            balance = balance.subtract(principalPart).max(BigDecimal.ZERO);
            rows.add(new DebtResponse.AmortizationRow(
                    due,
                    payment.setScale(2, RoundingMode.HALF_UP),
                    principalPart.setScale(2, RoundingMode.HALF_UP),
                    interest,
                    balance.setScale(2, RoundingMode.HALF_UP)
            ));
            due = due.plusMonths(1);
        }
        return rows;
    }
}
