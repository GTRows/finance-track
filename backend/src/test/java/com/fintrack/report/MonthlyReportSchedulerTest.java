package com.fintrack.report;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.entity.User;
import com.fintrack.notification.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReportSchedulerTest {

    @Mock UserRepository userRepository;
    @Mock ReportService reportService;
    @Mock MailService mailService;

    @InjectMocks MonthlyReportScheduler scheduler;

    private User user(String username, String email, boolean verified) {
        return User.builder()
                .id(UUID.randomUUID()).username(username).email(email)
                .password("pw").emailVerified(verified).build();
    }

    @BeforeEach
    void enable() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @Test
    void skipsWhenSchedulerDisabled() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.sendMonthlyReports();

        verify(mailService, never()).sendHtmlWithAttachment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsWhenMailServiceDisabled() {
        when(mailService.isEnabled()).thenReturn(false);

        scheduler.sendMonthlyReports();

        verify(userRepository, never()).findAll();
        verify(mailService, never()).sendHtmlWithAttachment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsUnverifiedUsers() {
        when(mailService.isEnabled()).thenReturn(true);
        when(userRepository.findAll()).thenReturn(List.of(
                user("unverified", "u@x", false),
                user("verified", "v@x", true)));
        when(reportService.generateMonthlyBudgetPdf(any(), any())).thenReturn(new byte[]{1, 2, 3});

        scheduler.sendMonthlyReports();

        verify(reportService, times(1)).generateMonthlyBudgetPdf(any(), any());
        verify(mailService).sendHtmlWithAttachment(
                eq("v@x"), anyString(), anyString(), anyString(), any(), eq("application/pdf"));
    }

    @Test
    void sendsPreviousMonthAsPdfAttachment() {
        when(mailService.isEnabled()).thenReturn(true);
        User u = user("ali", "ali@x", true);
        byte[] pdf = new byte[]{0x25, 0x50, 0x44, 0x46};
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(reportService.generateMonthlyBudgetPdf(eq(u.getId()), any(YearMonth.class))).thenReturn(pdf);

        scheduler.sendMonthlyReports();

        ArgumentCaptor<String> filename = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(mailService).sendHtmlWithAttachment(
                eq("ali@x"),
                anyString(),
                anyString(),
                filename.capture(),
                bytes.capture(),
                eq("application/pdf"));
        YearMonth expectedPeriod = YearMonth.now().minusMonths(1);
        assertThat(filename.getValue()).isEqualTo("fintrack-" + expectedPeriod + ".pdf");
        assertThat(bytes.getValue()).isEqualTo(pdf);
    }

    @Test
    void continuesOnFailureForOneUser() {
        when(mailService.isEnabled()).thenReturn(true);
        User bad = user("bad", "bad@x", true);
        User good = user("good", "good@x", true);
        when(userRepository.findAll()).thenReturn(List.of(bad, good));
        when(reportService.generateMonthlyBudgetPdf(eq(bad.getId()), any())).thenThrow(new RuntimeException("boom"));
        when(reportService.generateMonthlyBudgetPdf(eq(good.getId()), any())).thenReturn(new byte[]{1});

        scheduler.sendMonthlyReports();

        verify(mailService, times(1)).sendHtmlWithAttachment(
                eq("good@x"), anyString(), anyString(), anyString(), any(), anyString());
    }
}
