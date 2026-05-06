package com.fintrack.bills;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.entity.User;
import com.fintrack.common.event.BillPaidEvent;
import com.fintrack.notification.MailService;
import com.fintrack.notification.MailTemplate;
import com.fintrack.push.PushService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends a confirmation email and wakes push subscribers after a bill payment commits. Skips both
 * channels when the user record is missing or the email is unverified, mirroring the existing
 * {@link BillReminderScheduler} behaviour.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillPaidNotificationListener {

    private final UserRepository userRepository;
    private final MailService mailService;
    private final PushService pushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBillPaid(BillPaidEvent event) {
        Optional<User> maybeUser = userRepository.findById(event.userId());
        if (maybeUser.isEmpty()) {
            log.debug("Bill paid event for unknown user {}; skipping notification", event.userId());
            return;
        }
        User user = maybeUser.get();
        if (!user.isEmailVerified()) {
            log.debug(
                    "Skipping bill payment confirmation for unverified user {}",
                    user.getUsername());
            return;
        }

        try {
            mailService.sendHtml(user.getEmail(), buildSubject(event), buildBody(user, event));
        } catch (Exception e) {
            log.warn(
                    "Failed to send bill payment confirmation to {}: {}",
                    user.getUsername(),
                    e.getMessage());
        }

        try {
            pushService.sendToUser(user.getId());
        } catch (Exception e) {
            log.warn(
                    "Push notification failed for bill payment to {}: {}",
                    user.getUsername(),
                    e.getMessage());
        }
    }

    private String buildSubject(BillPaidEvent event) {
        return "Payment recorded: "
                + event.billName()
                + " "
                + event.amount().toPlainString()
                + " "
                + event.currency();
    }

    private String buildBody(User user, BillPaidEvent event) {
        String inner =
                """
<h2 style="margin:0 0 12px;color:#f8fafc;font-size:20px">Payment recorded</h2>
<p>Hi %s, your payment for <strong>%s</strong> (%s) has been recorded.</p>
<p style="margin:16px 0;color:#cbd5e1">Amount: %s %s</p>
<p style="font-size:12px;color:#64748b">You can review payment history inside FinTrack Pro.</p>
"""
                        .formatted(
                                MailTemplate.escape(user.getUsername()),
                                MailTemplate.escape(event.billName()),
                                MailTemplate.escape(event.period()),
                                event.amount().toPlainString(),
                                MailTemplate.escape(event.currency()));
        return MailTemplate.wrap(inner);
    }
}
