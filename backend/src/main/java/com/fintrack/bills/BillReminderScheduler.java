package com.fintrack.bills;

import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Daily bill reminder check. For each active bill, if today is within
 * `remindDaysBefore` days of the upcoming due date and the bill is not yet
 * paid for the current period, emit a reminder log line that downstream
 * notification infrastructure can consume.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillReminderScheduler {

    private final BillRepository billRepo;
    private final BillPaymentRepository paymentRepo;

    /** Runs every day at 08:00 local time. */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional(readOnly = true)
    public void checkReminders() {
        LocalDate today = LocalDate.now();
        String period = today.getYear() + "-" + String.format("%02d", today.getMonthValue());
        int reminders = 0;

        for (Bill bill : billRepo.findAll()) {
            if (!bill.isActive()) continue;

            LocalDate dueDate = nextDueDate(today, bill.getDueDay());
            long daysUntil = ChronoUnit.DAYS.between(today, dueDate);
            if (daysUntil < 0 || daysUntil > bill.getRemindDaysBefore()) continue;

            Optional<BillPayment> payment = paymentRepo.findByBillIdAndPeriod(bill.getId(), period);
            if (payment.isPresent() && payment.get().getStatus() == BillPayment.PaymentStatus.PAID) continue;

            log.info("Bill reminder: userId={} billId={} name={} dueDate={} daysUntil={} amount={}",
                    bill.getUserId(), bill.getId(), bill.getName(), dueDate, daysUntil, bill.getAmount());
            reminders++;
        }

        log.info("Bill reminder pass complete: reminders={}", reminders);
    }

    private LocalDate nextDueDate(LocalDate today, int dueDay) {
        int thisMonthLen = today.lengthOfMonth();
        LocalDate candidate = today.withDayOfMonth(Math.min(dueDay, thisMonthLen));
        if (candidate.isBefore(today)) {
            LocalDate nextMonth = today.plusMonths(1);
            return nextMonth.withDayOfMonth(Math.min(dueDay, nextMonth.lengthOfMonth()));
        }
        return candidate;
    }
}
