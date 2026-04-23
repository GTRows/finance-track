package com.fintrack.bills;

import com.fintrack.auth.UserRepository;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.BillPayment.PaymentStatus;
import com.fintrack.common.entity.User;
import com.fintrack.notification.MailService;
import com.fintrack.push.PushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillReminderSchedulerTest {

    @Mock BillRepository billRepo;
    @Mock BillPaymentRepository paymentRepo;
    @Mock UserRepository userRepository;
    @Mock MailService mailService;
    @Mock PushService pushService;

    @InjectMocks BillReminderScheduler scheduler;

    private final UUID userId = UUID.randomUUID();

    private User user(boolean emailVerified) {
        return User.builder()
                .id(userId).username("ali").email("ali@example.com")
                .password("pw").emailVerified(emailVerified).build();
    }

    private Bill bill(int dueDay, int remindDaysBefore, LocalDate lastRemindedOn, boolean active) {
        return Bill.builder()
                .id(UUID.randomUUID()).userId(userId)
                .name("Internet").amount(new BigDecimal("150"))
                .currency("TRY")
                .dueDay(dueDay).remindDaysBefore(remindDaysBefore)
                .lastRemindedOn(lastRemindedOn).active(active)
                .build();
    }

    private Bill billDueToday(int remindDaysBefore, LocalDate lastRemindedOn, boolean active) {
        return bill(LocalDate.now().getDayOfMonth(), remindDaysBefore, lastRemindedOn, active);
    }

    @Test
    void skipsInactiveBills() {
        Bill inactive = billDueToday(3, null, false);
        when(billRepo.findAll()).thenReturn(List.of(inactive));

        scheduler.checkReminders();

        verify(mailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void skipsBillOutsideReminderWindow() {
        int today = LocalDate.now().getDayOfMonth();
        int tooFar = today + 10;
        if (tooFar > 28) return;
        Bill farFuture = bill(tooFar, 3, null, true);
        when(billRepo.findAll()).thenReturn(List.of(farFuture));

        scheduler.checkReminders();

        verify(mailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void skipsBillAlreadyPaidThisPeriod() {
        Bill paid = billDueToday(3, null, true);
        BillPayment p = BillPayment.builder()
                .id(UUID.randomUUID()).billId(paid.getId())
                .period(LocalDate.now().getYear() + "-" + String.format("%02d", LocalDate.now().getMonthValue()))
                .amount(new BigDecimal("150")).status(PaymentStatus.PAID).build();
        when(billRepo.findAll()).thenReturn(List.of(paid));
        when(paymentRepo.findByBillIdAndPeriod(eq(paid.getId()), anyString())).thenReturn(Optional.of(p));

        scheduler.checkReminders();

        verify(mailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void skipsBillAlreadyRemindedInCurrentWindow() {
        Bill remindedToday = billDueToday(3, LocalDate.now(), true);
        when(billRepo.findAll()).thenReturn(List.of(remindedToday));

        scheduler.checkReminders();

        verify(mailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void skipsUnverifiedUsers() {
        Bill b = billDueToday(3, null, true);
        when(billRepo.findAll()).thenReturn(List.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(false)));

        scheduler.checkReminders();

        verify(mailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void skipsUsersNoLongerPresentInDb() {
        Bill b = billDueToday(3, null, true);
        when(billRepo.findAll()).thenReturn(List.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        scheduler.checkReminders();

        verify(mailService, never()).sendHtml(any(), any(), any());
    }

    @Test
    void sendsReminderAndStampsLastRemindedOn() {
        Bill b = billDueToday(3, null, true);
        when(billRepo.findAll()).thenReturn(List.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true)));

        scheduler.checkReminders();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendHtml(eq("ali@example.com"), subject.capture(), body.capture());
        assertThat(subject.getValue()).contains("Internet");
        assertThat(body.getValue()).contains("ali").contains("150");
        assertThat(b.getLastRemindedOn()).isEqualTo(LocalDate.now());
        verify(billRepo).save(b);
        verify(pushService).sendToUser(userId);
    }

    @Test
    void multipleBillsSummaryUsesCountSubject() {
        Bill b1 = billDueToday(5, null, true);
        Bill b2 = billDueToday(5, null, true);
        b2.setName("Electric");
        b2.setAmount(new BigDecimal("250"));
        when(billRepo.findAll()).thenReturn(List.of(b1, b2));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true)));

        scheduler.checkReminders();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendHtml(eq("ali@example.com"), subject.capture(), anyString());
        assertThat(subject.getValue()).contains("2 bills");
    }

    @Test
    void mailFailureIsSwallowedAndLastRemindedOnNotStamped() {
        Bill b = billDueToday(3, null, true);
        when(billRepo.findAll()).thenReturn(List.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true)));
        doThrow(new RuntimeException("smtp down")).when(mailService).sendHtml(any(), any(), any());

        scheduler.checkReminders();

        assertThat(b.getLastRemindedOn()).isNull();
        verify(billRepo, never()).save(any());
        verify(pushService).sendToUser(userId);
    }

    @Test
    void pushFailureDoesNotStopProcessing() {
        Bill b = billDueToday(3, null, true);
        when(billRepo.findAll()).thenReturn(List.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true)));
        when(pushService.sendToUser(userId)).thenThrow(new RuntimeException("wp down"));

        scheduler.checkReminders();

        verify(mailService).sendHtml(any(), any(), any());
        assertThat(b.getLastRemindedOn()).isEqualTo(LocalDate.now());
    }
}
