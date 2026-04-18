package com.fintrack.report;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.entity.User;
import com.fintrack.notification.MailService;
import com.fintrack.notification.MailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * First-of-month pass that emails each user a PDF digest of the previous month.
 * Gated on {@code fintrack.monthly-report.enabled}; defaults to off so existing
 * deployments do not start mailing unannounced after upgrade.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyReportScheduler {

    private static final DateTimeFormatter SUBJECT_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final ReportService reportService;
    private final MailService mailService;

    @Value("${fintrack.monthly-report.enabled:false}")
    private boolean enabled;

    /** Runs on the 1st of each month at 09:00 Europe/Istanbul. */
    @Scheduled(cron = "0 0 9 1 * *", zone = "Europe/Istanbul")
    public void sendMonthlyReports() {
        if (!enabled) {
            log.debug("Monthly report scheduler disabled, skipping run");
            return;
        }
        if (!mailService.isEnabled()) {
            log.warn("Monthly report scheduler skipped: mail is not configured");
            return;
        }

        YearMonth period = YearMonth.now().minusMonths(1);
        String monthLabel = period.format(SUBJECT_FMT);
        String fileStem = period.format(FILE_FMT);
        int sent = 0;

        for (User user : userRepository.findAll()) {
            if (!user.isEmailVerified()) continue;
            try {
                byte[] pdf = reportService.generateMonthlyBudgetPdf(user.getId(), period);
                String subject = "FinTrack Pro monthly report - " + monthLabel;
                String body = buildBody(user, monthLabel);
                String filename = "fintrack-" + fileStem + ".pdf";
                mailService.sendHtmlWithAttachment(user.getEmail(), subject, body,
                        filename, pdf, "application/pdf");
                sent++;
            } catch (Exception e) {
                log.warn("Failed to build monthly report for user {}: {}", user.getUsername(), e.getMessage());
            }
        }

        log.info("Monthly report pass complete: period={} users={}", period, sent);
    }

    private String buildBody(User user, String monthLabel) {
        String inner = """
                <h2 style="margin:0 0 12px;color:#f8fafc;font-size:20px">Your %s report</h2>
                <p>Hi %s, your monthly FinTrack Pro summary for <strong>%s</strong> is attached as a PDF.</p>
                <p>It covers income, expenses, net cash flow, savings rate, category breakdown, and the full transaction list for the period.</p>
                <p style="font-size:12px;color:#64748b">Open FinTrack Pro to explore the live dashboard or adjust category budgets for the new month.</p>
                """.formatted(
                MailTemplate.escape(monthLabel),
                MailTemplate.escape(user.getUsername()),
                MailTemplate.escape(monthLabel));
        return MailTemplate.wrap(inner);
    }
}
