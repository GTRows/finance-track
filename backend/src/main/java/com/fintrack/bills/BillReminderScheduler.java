package com.fintrack.bills;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.User;
import com.fintrack.notification.MailService;
import com.fintrack.notification.MailTemplate;
import com.fintrack.push.PushService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily pass that emails a digest of upcoming bill reminders per user. Each bill is reminded at
 * most once per reminder window via the {@code lastRemindedOn} stamp, so a missed scheduler run
 * still triggers the email the next day.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillReminderScheduler {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM");

    private final BillRepository billRepo;
    private final BillPaymentRepository paymentRepo;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final PushService pushService;

    /** Runs every day at 08:00 local time. */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void checkReminders() {
        LocalDate today = LocalDate.now();
        String period = today.getYear() + "-" + String.format("%02d", today.getMonthValue());
        Map<UUID, List<PendingReminder>> perUser = new HashMap<>();

        for (Bill bill : billRepo.findAll()) {
            if (!bill.isActive()) continue;

            LocalDate dueDate = nextDueDate(today, bill.getDueDay());
            long daysUntil = ChronoUnit.DAYS.between(today, dueDate);
            if (daysUntil < 0 || daysUntil > bill.getRemindDaysBefore()) continue;

            LocalDate windowStart = dueDate.minusDays(bill.getRemindDaysBefore());
            LocalDate last = bill.getLastRemindedOn();
            if (last != null && !last.isBefore(windowStart)) continue;

            Optional<BillPayment> payment = paymentRepo.findByBillIdAndPeriod(bill.getId(), period);
            if (payment.isPresent() && payment.get().getStatus() == BillPayment.PaymentStatus.PAID)
                continue;

            perUser.computeIfAbsent(bill.getUserId(), k -> new ArrayList<>())
                    .add(new PendingReminder(bill, dueDate, daysUntil));
        }

        int delivered = 0;
        for (Map.Entry<UUID, List<PendingReminder>> entry : perUser.entrySet()) {
            Optional<User> maybeUser = userRepository.findById(entry.getKey());
            if (maybeUser.isEmpty()) continue;
            User user = maybeUser.get();
            if (!user.isEmailVerified()) {
                log.debug("Skipping bill reminders for unverified user {}", user.getUsername());
                continue;
            }

            try {
                mailService.sendHtml(
                        user.getEmail(),
                        buildSubject(entry.getValue()),
                        buildBody(user, entry.getValue()));
                for (PendingReminder pr : entry.getValue()) {
                    pr.bill.setLastRemindedOn(today);
                    billRepo.save(pr.bill);
                }
                delivered += entry.getValue().size();
            } catch (Exception e) {
                log.warn(
                        "Failed to send bill reminder to {}: {}",
                        user.getUsername(),
                        e.getMessage());
            }

            try {
                int woken = pushService.sendToUser(user.getId());
                if (woken > 0)
                    log.debug(
                            "Bill reminder push woke {} device(s) for {}",
                            woken,
                            user.getUsername());
            } catch (Exception e) {
                log.warn("Push reminder failed for {}: {}", user.getUsername(), e.getMessage());
            }
        }

        log.info("Bill reminder pass complete: users={} reminders={}", perUser.size(), delivered);
    }

    private String buildSubject(List<PendingReminder> pending) {
        if (pending.size() == 1) {
            PendingReminder pr = pending.getFirst();
            return "Reminder: " + pr.bill.getName() + " due " + pr.dueDate.format(DATE_FORMAT);
        }
        return pending.size() + " bills due soon";
    }

    private String buildBody(User user, List<PendingReminder> pending) {
        BigDecimal total =
                pending.stream()
                        .map(pr -> pr.bill.getAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder rows = new StringBuilder();
        for (PendingReminder pr : pending) {
            String due = pr.dueDate.format(DATE_FORMAT);
            String when =
                    pr.daysUntil == 0
                            ? "today"
                            : pr.daysUntil + " day" + (pr.daysUntil == 1 ? "" : "s");
            rows.append(
                    """
                    <tr>
                      <td style="padding:8px 0;color:#f8fafc;font-weight:500">%s</td>
                      <td style="padding:8px 0;color:#cbd5e1;text-align:right">%s %s</td>
                      <td style="padding:8px 0;color:#94a3b8;text-align:right">%s (%s)</td>
                    </tr>
                    """
                            .formatted(
                                    MailTemplate.escape(pr.bill.getName()),
                                    pr.bill.getAmount().toPlainString(),
                                    MailTemplate.escape(pr.bill.getCurrency()),
                                    due,
                                    when));
        }
        String inner =
                """
<h2 style="margin:0 0 12px;color:#f8fafc;font-size:20px">Upcoming bills</h2>
<p>Hi %s, you have %d bill(s) coming up. Total %s TRY.</p>
<table width="100%%" cellpadding="0" cellspacing="0" role="presentation" style="border-top:1px solid #334155;border-bottom:1px solid #334155;margin:16px 0">
  %s
</table>
<p style="font-size:12px;color:#64748b">Mark a bill as paid inside FinTrack Pro to stop future reminders for this period.</p>
"""
                        .formatted(
                                MailTemplate.escape(user.getUsername()),
                                pending.size(),
                                total.toPlainString(),
                                rows);
        return MailTemplate.wrap(inner);
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

    private record PendingReminder(Bill bill, LocalDate dueDate, long daysUntil) {}
}
