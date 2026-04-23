package com.fintrack.debt;

import com.fintrack.common.entity.Debt;
import com.fintrack.common.entity.DebtPayment;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.debt.dto.DebtPaymentRequest;
import com.fintrack.debt.dto.DebtPaymentResponse;
import com.fintrack.debt.dto.DebtResponse;
import com.fintrack.debt.dto.UpsertDebtRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

    @Mock DebtRepository debtRepo;
    @Mock DebtPaymentRepository paymentRepo;

    @InjectMocks DebtService service;

    private final UUID userId = UUID.randomUUID();

    private Debt debt(BigDecimal principal, String rate, int term, LocalDate start) {
        return Debt.builder()
                .id(UUID.randomUUID()).userId(userId)
                .name("Loan").debtType("LOAN")
                .principal(principal)
                .annualRate(new BigDecimal(rate))
                .termMonths(term)
                .startDate(start)
                .build();
    }

    @Test
    void createPersistsRequestFields() {
        UpsertDebtRequest req = new UpsertDebtRequest(
                "Car", "CAR", new BigDecimal("120000"), new BigDecimal("0.2000"),
                24, LocalDate.of(2026, 1, 1), "notes");
        when(debtRepo.save(any(Debt.class))).thenAnswer(inv -> {
            Debt d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DebtResponse res = service.create(userId, req);

        ArgumentCaptor<Debt> captor = ArgumentCaptor.forClass(Debt.class);
        verify(debtRepo).save(captor.capture());
        Debt saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPrincipal()).isEqualByComparingTo("120000");
        assertThat(saved.getTermMonths()).isEqualTo(24);
        assertThat(res.status()).isEqualTo("ACTIVE");
        assertThat(res.totalActuallyPaid()).isEqualByComparingTo("0");
    }

    @Test
    void updateRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(debtRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, id, new UpsertDebtRequest(
                "x", "LOAN", BigDecimal.TEN, BigDecimal.ZERO, 1, LocalDate.now(), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMutatesFieldsAndReturnsCurrentPaidTotal() {
        Debt existing = debt(new BigDecimal("10000"), "0.0000", 10, LocalDate.of(2026, 1, 1));
        when(debtRepo.findByIdAndUserId(existing.getId(), userId)).thenReturn(Optional.of(existing));
        when(paymentRepo.sumByDebtId(existing.getId())).thenReturn(new BigDecimal("3000"));

        DebtResponse res = service.update(userId, existing.getId(), new UpsertDebtRequest(
                "Updated", "CREDIT", new BigDecimal("12000"), new BigDecimal("0.1000"),
                12, LocalDate.of(2026, 2, 1), "updated"));

        assertThat(existing.getName()).isEqualTo("Updated");
        assertThat(existing.getPrincipal()).isEqualByComparingTo("12000");
        assertThat(existing.getTermMonths()).isEqualTo(12);
        assertThat(res.totalActuallyPaid()).isEqualByComparingTo("3000");
    }

    @Test
    void archiveSetsTimestamp() {
        Debt existing = debt(new BigDecimal("1000"), "0.0000", 12, LocalDate.now());
        when(debtRepo.findByIdAndUserId(existing.getId(), userId)).thenReturn(Optional.of(existing));

        service.archive(userId, existing.getId());

        assertThat(existing.getArchivedAt()).isNotNull();
    }

    @Test
    void listZeroInterestUsesFlatSchedule() {
        Debt d = debt(new BigDecimal("12000"), "0.0000", 12, LocalDate.of(2026, 1, 1));
        when(debtRepo.findActive(userId)).thenReturn(List.of(d));
        when(paymentRepo.sumByDebtId(d.getId())).thenReturn(BigDecimal.ZERO);

        List<DebtResponse> res = service.list(userId);

        assertThat(res).hasSize(1);
        DebtResponse row = res.get(0);
        assertThat(row.scheduledMonthlyPayment()).isEqualByComparingTo("1000.00");
        assertThat(row.totalScheduledPaid()).isEqualByComparingTo("12000.00");
        assertThat(row.totalInterest()).isEqualByComparingTo("0");
        assertThat(row.remainingBalance()).isEqualByComparingTo("12000.00");
        assertThat(row.status()).isEqualTo("ACTIVE");
    }

    @Test
    void nonZeroInterestAddsInterestToTotalScheduled() {
        Debt d = debt(new BigDecimal("120000"), "0.1200", 12, LocalDate.of(2026, 1, 1));
        when(debtRepo.findActive(userId)).thenReturn(List.of(d));
        when(paymentRepo.sumByDebtId(d.getId())).thenReturn(BigDecimal.ZERO);

        DebtResponse res = service.list(userId).get(0);

        assertThat(res.scheduledMonthlyPayment()).isGreaterThan(new BigDecimal("10000"));
        assertThat(res.totalScheduledPaid()).isGreaterThan(new BigDecimal("120000"));
        assertThat(res.totalInterest()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void actuallyPaidReducesRemainingBalance() {
        Debt d = debt(new BigDecimal("12000"), "0.0000", 12, LocalDate.of(2026, 1, 1));
        when(debtRepo.findActive(userId)).thenReturn(List.of(d));
        when(paymentRepo.sumByDebtId(d.getId())).thenReturn(new BigDecimal("4000"));

        DebtResponse res = service.list(userId).get(0);

        assertThat(res.remainingBalance()).isEqualByComparingTo("8000.00");
        assertThat(res.progressRatio()).isEqualByComparingTo("0.3333");
    }

    @Test
    void fullyPaidFlipsStatusAndZerosPreview() {
        Debt d = debt(new BigDecimal("6000"), "0.0000", 6, LocalDate.of(2026, 1, 1));
        when(debtRepo.findActive(userId)).thenReturn(List.of(d));
        when(paymentRepo.sumByDebtId(d.getId())).thenReturn(new BigDecimal("6000"));

        DebtResponse res = service.list(userId).get(0);

        assertThat(res.status()).isEqualTo("PAID_OFF");
        assertThat(res.remainingBalance()).isEqualByComparingTo("0.00");
        assertThat(res.progressRatio()).isEqualByComparingTo("1.0000");
        assertThat(res.projectedPayoffDate()).isNull();
        assertThat(res.nextPayments()).isEmpty();
    }

    @Test
    void amortizationPreviewHasUpToSixRowsAndDecreasingBalance() {
        Debt d = debt(new BigDecimal("12000"), "0.1200", 24, LocalDate.of(2026, 1, 1));
        when(debtRepo.findActive(userId)).thenReturn(List.of(d));
        when(paymentRepo.sumByDebtId(d.getId())).thenReturn(BigDecimal.ZERO);

        List<DebtResponse.AmortizationRow> preview = service.list(userId).get(0).nextPayments();

        assertThat(preview).hasSize(6);
        BigDecimal prev = d.getPrincipal();
        for (DebtResponse.AmortizationRow row : preview) {
            assertThat(row.remainingBalance()).isLessThan(prev);
            assertThat(row.principal()).isGreaterThan(BigDecimal.ZERO);
            prev = row.remainingBalance();
        }
    }

    @Test
    void addPaymentSavesAndRequiresOwnership() {
        Debt d = debt(new BigDecimal("1000"), "0.0000", 12, LocalDate.of(2026, 1, 1));
        when(debtRepo.findByIdAndUserId(d.getId(), userId)).thenReturn(Optional.of(d));
        when(paymentRepo.save(any(DebtPayment.class))).thenAnswer(inv -> {
            DebtPayment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        DebtPaymentResponse res = service.addPayment(userId, d.getId(),
                new DebtPaymentRequest(LocalDate.of(2026, 2, 1), new BigDecimal("250"), "feb"));

        ArgumentCaptor<DebtPayment> captor = ArgumentCaptor.forClass(DebtPayment.class);
        verify(paymentRepo).save(captor.capture());
        assertThat(captor.getValue().getDebtId()).isEqualTo(d.getId());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("250");
        assertThat(res.amount()).isEqualByComparingTo("250");
    }

    @Test
    void addPaymentThrowsWhenDebtNotOwned() {
        UUID debtId = UUID.randomUUID();
        when(debtRepo.findByIdAndUserId(debtId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPayment(userId, debtId,
                new DebtPaymentRequest(LocalDate.now(), BigDecimal.TEN, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void deletePaymentRejectsWhenPaymentBelongsToAnotherDebt() {
        Debt d = debt(new BigDecimal("1000"), "0.0000", 12, LocalDate.of(2026, 1, 1));
        when(debtRepo.findByIdAndUserId(d.getId(), userId)).thenReturn(Optional.of(d));
        DebtPayment foreign = DebtPayment.builder()
                .id(UUID.randomUUID()).debtId(UUID.randomUUID())
                .paymentDate(LocalDate.now()).amount(BigDecimal.TEN).build();
        when(paymentRepo.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.deletePayment(userId, d.getId(), foreign.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(paymentRepo, never()).delete(any());
    }

    @Test
    void deletePaymentRemovesWhenMatch() {
        Debt d = debt(new BigDecimal("1000"), "0.0000", 12, LocalDate.of(2026, 1, 1));
        when(debtRepo.findByIdAndUserId(d.getId(), userId)).thenReturn(Optional.of(d));
        DebtPayment p = DebtPayment.builder()
                .id(UUID.randomUUID()).debtId(d.getId())
                .paymentDate(LocalDate.now()).amount(BigDecimal.TEN).build();
        when(paymentRepo.findById(p.getId())).thenReturn(Optional.of(p));

        service.deletePayment(userId, d.getId(), p.getId());

        verify(paymentRepo).delete(p);
    }

    @Test
    void listPaymentsThrowsWhenDebtNotOwned() {
        UUID debtId = UUID.randomUUID();
        when(debtRepo.findByIdAndUserId(debtId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listPayments(userId, debtId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPaymentsReturnsMappedRows() {
        Debt d = debt(new BigDecimal("1000"), "0.0000", 12, LocalDate.of(2026, 1, 1));
        when(debtRepo.findByIdAndUserId(d.getId(), userId)).thenReturn(Optional.of(d));
        DebtPayment p = DebtPayment.builder()
                .id(UUID.randomUUID()).debtId(d.getId())
                .paymentDate(LocalDate.of(2026, 3, 1))
                .amount(new BigDecimal("77")).note("partial").build();
        when(paymentRepo.findByDebtIdOrderByPaymentDateAsc(d.getId())).thenReturn(List.of(p));

        List<DebtPaymentResponse> res = service.listPayments(userId, d.getId());

        assertThat(res).hasSize(1);
        assertThat(res.get(0).amount()).isEqualByComparingTo("77");
        assertThat(res.get(0).note()).isEqualTo("partial");
    }
}
