package com.fintrack.bills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.bills.dto.BillResponse;
import com.fintrack.bills.dto.BillVarianceDto;
import com.fintrack.bills.dto.CreateBillRequest;
import com.fintrack.bills.dto.PayBillRequest;
import com.fintrack.bills.dto.SubscriptionAuditDto;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment;
import com.fintrack.common.entity.BillPayment.PaymentStatus;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock BillRepository billRepo;
    @Mock BillPaymentRepository paymentRepo;

    @InjectMocks BillService service;

    private final UUID userId = UUID.randomUUID();

    private Bill bill(String name, String amount, int dueDay) {
        return Bill.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .amount(new BigDecimal(amount))
                .currency("TRY")
                .category("utilities")
                .dueDay(dueDay)
                .active(true)
                .remindDaysBefore(3)
                .build();
    }

    @Test
    void createPersistsRequestedFields() {
        when(billRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillResponse res =
                service.create(
                        userId,
                        new CreateBillRequest(
                                "Electric",
                                new BigDecimal("350"),
                                15,
                                "utilities",
                                3,
                                false,
                                "note"));

        assertThat(res.name()).isEqualTo("Electric");
        assertThat(res.amount()).isEqualByComparingTo("350");
        assertThat(res.dueDay()).isEqualTo(15);

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getNotes()).isEqualTo("note");
    }

    @Test
    void updateThrowsWhenNotOwned() {
        UUID id = UUID.randomUUID();
        when(billRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId,
                                        id,
                                        new CreateBillRequest(
                                                "x", BigDecimal.ONE, 1, "", 3, false, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void payRecordsNewPaymentAtBillAmountWhenAmountOmitted() {
        Bill b = bill("Electric", "350", 15);
        when(billRepo.findByIdAndUserId(b.getId(), userId)).thenReturn(Optional.of(b));
        when(paymentRepo.findByBillIdAndPeriod(b.getId(), "2026-04")).thenReturn(Optional.empty());
        when(paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(b.getId(), PaymentStatus.PAID))
                .thenReturn(List.of());

        service.pay(userId, b.getId(), new PayBillRequest("2026-04", null, null));

        ArgumentCaptor<BillPayment> captor = ArgumentCaptor.forClass(BillPayment.class);
        verify(paymentRepo).save(captor.capture());
        BillPayment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getAmount()).isEqualByComparingTo("350");
        assertThat(saved.getPeriod()).isEqualTo("2026-04");
        assertThat(saved.getPaidAt()).isNotNull();
    }

    @Test
    void payOverridesExistingPaymentForThePeriod() {
        Bill b = bill("Electric", "350", 15);
        BillPayment existing =
                BillPayment.builder()
                        .id(UUID.randomUUID())
                        .billId(b.getId())
                        .period("2026-04")
                        .amount(new BigDecimal("300"))
                        .status(PaymentStatus.PENDING)
                        .build();
        when(billRepo.findByIdAndUserId(b.getId(), userId)).thenReturn(Optional.of(b));
        when(paymentRepo.findByBillIdAndPeriod(b.getId(), "2026-04"))
                .thenReturn(Optional.of(existing));
        when(paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(b.getId(), PaymentStatus.PAID))
                .thenReturn(List.of());

        service.pay(
                userId,
                b.getId(),
                new PayBillRequest("2026-04", new BigDecimal("375.50"), "paid online"));

        assertThat(existing.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(existing.getAmount()).isEqualByComparingTo("375.50");
        assertThat(existing.getNotes()).isEqualTo("paid online");
        verify(paymentRepo).save(existing);
    }

    @Test
    void payThrowsWhenBillNotOwned() {
        UUID id = UUID.randomUUID();
        when(billRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(userId, id, new PayBillRequest("2026-04", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void historyReturnsMappedRowsForOwnedBill() {
        Bill b = bill("Gym", "200", 10);
        when(billRepo.findByIdAndUserId(b.getId(), userId)).thenReturn(Optional.of(b));
        when(paymentRepo.findByBillIdOrderByPeriodDesc(b.getId()))
                .thenReturn(
                        List.of(
                                BillPayment.builder()
                                        .id(UUID.randomUUID())
                                        .billId(b.getId())
                                        .period("2026-03")
                                        .amount(new BigDecimal("200"))
                                        .status(PaymentStatus.PAID)
                                        .paidAt(Instant.now())
                                        .build()));

        assertThat(service.history(userId, b.getId())).hasSize(1);
    }

    @Test
    void deleteRemovesOwnedBill() {
        Bill b = bill("Gym", "200", 10);
        when(billRepo.findByIdAndUserId(b.getId(), userId)).thenReturn(Optional.of(b));

        service.delete(userId, b.getId());

        verify(billRepo).delete(b);
    }

    @Test
    void markUsedUpdatesLastUsedToday() {
        Bill b = bill("Spotify", "100", 5);
        when(billRepo.findByIdAndUserId(b.getId(), userId)).thenReturn(Optional.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(any(), any()))
                .thenReturn(List.of());

        service.markUsed(userId, b.getId());

        assertThat(b.getLastUsedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    void listFlagsVarianceWhenLatestPaidJumpsOver10PercentAnd25Absolute() {
        Bill b = bill("Electric", "350", 15);
        when(billRepo.findByUserIdOrderByDueDayAsc(userId)).thenReturn(List.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(b.getId(), PaymentStatus.PAID))
                .thenReturn(
                        List.of(
                                BillPayment.builder()
                                        .billId(b.getId())
                                        .period("2026-03")
                                        .amount(new BigDecimal("420"))
                                        .status(PaymentStatus.PAID)
                                        .build(),
                                BillPayment.builder()
                                        .billId(b.getId())
                                        .period("2026-02")
                                        .amount(new BigDecimal("300"))
                                        .status(PaymentStatus.PAID)
                                        .build()));

        List<BillResponse> res = service.listForUser(userId);

        BillVarianceDto v = res.get(0).variance();
        assertThat(v).isNotNull();
        assertThat(v.flagged()).isTrue();
        assertThat(v.delta()).isEqualByComparingTo("120");
        assertThat(v.deltaPercent()).isEqualByComparingTo("40.00");
    }

    @Test
    void listDoesNotFlagVarianceForSmallDelta() {
        Bill b = bill("Water", "120", 8);
        when(billRepo.findByUserIdOrderByDueDayAsc(userId)).thenReturn(List.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(b.getId(), PaymentStatus.PAID))
                .thenReturn(
                        List.of(
                                BillPayment.builder()
                                        .billId(b.getId())
                                        .period("2026-03")
                                        .amount(new BigDecimal("121"))
                                        .status(PaymentStatus.PAID)
                                        .build(),
                                BillPayment.builder()
                                        .billId(b.getId())
                                        .period("2026-02")
                                        .amount(new BigDecimal("120"))
                                        .status(PaymentStatus.PAID)
                                        .build()));

        List<BillResponse> res = service.listForUser(userId);

        assertThat(res.get(0).variance().flagged()).isFalse();
    }

    @Test
    void listOmitsVarianceWhenFewerThanTwoPayments() {
        Bill b = bill("Gym", "200", 10);
        when(billRepo.findByUserIdOrderByDueDayAsc(userId)).thenReturn(List.of(b));
        when(paymentRepo.findByBillIdAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(b.getId(), PaymentStatus.PAID))
                .thenReturn(
                        List.of(
                                BillPayment.builder()
                                        .billId(b.getId())
                                        .period("2026-03")
                                        .amount(new BigDecimal("200"))
                                        .status(PaymentStatus.PAID)
                                        .build()));

        assertThat(service.listForUser(userId).get(0).variance()).isNull();
    }

    @Test
    void auditFlagsNeverUsedOldBill() {
        Bill old = bill("Old Sub", "150", 1);
        old.setCreatedAt(Instant.now().minus(120, ChronoUnit.DAYS));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of(old));

        SubscriptionAuditDto audit = service.audit(userId);

        assertThat(audit.totalMonthlySpend()).isEqualByComparingTo("150");
        assertThat(audit.candidates()).hasSize(1);
        assertThat(audit.candidates().get(0).reason()).isEqualTo("NEVER_USED");
        assertThat(audit.potentialMonthlySavings()).isEqualByComparingTo("150");
    }

    @Test
    void auditFlagsStaleBillAndIgnoresRecentUse() {
        Bill stale = bill("Old Magazine", "90", 2);
        stale.setCreatedAt(Instant.now().minus(200, ChronoUnit.DAYS));
        stale.setLastUsedOn(LocalDate.now().minusDays(120));

        Bill fresh = bill("Active App", "50", 3);
        fresh.setCreatedAt(Instant.now().minus(200, ChronoUnit.DAYS));
        fresh.setLastUsedOn(LocalDate.now().minusDays(10));

        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId))
                .thenReturn(List.of(stale, fresh));

        SubscriptionAuditDto audit = service.audit(userId);

        assertThat(audit.candidates())
                .extracting(SubscriptionAuditDto.Candidate::name)
                .containsExactly("Old Magazine");
        assertThat(audit.candidates().get(0).reason()).isEqualTo("STALE");
    }

    @Test
    void auditIgnoresBillsNewerThanMinimumAge() {
        Bill young = bill("Brand New", "100", 1);
        young.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        when(billRepo.findByUserIdAndActiveTrueOrderByDueDayAsc(userId)).thenReturn(List.of(young));

        SubscriptionAuditDto audit = service.audit(userId);

        assertThat(audit.candidates()).isEmpty();
        assertThat(audit.totalMonthlySpend()).isEqualByComparingTo("100");
    }
}
