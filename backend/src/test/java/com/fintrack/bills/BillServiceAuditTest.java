package com.fintrack.bills;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.audit.AuditAction;
import com.fintrack.audit.AuditService;
import com.fintrack.bills.dto.CreateBillRequest;
import com.fintrack.bills.dto.PayBillRequest;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BillPayment.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BillServiceAuditTest {

    @Mock BillRepository billRepo;
    @Mock BillPaymentRepository paymentRepo;
    @Mock AuditService auditService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks BillService service;

    private final UUID userId = UUID.randomUUID();

    private Bill bill() {
        return Bill.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Electric")
                .amount(new BigDecimal("350"))
                .currency("TRY")
                .category("utilities")
                .dueDay(15)
                .active(true)
                .remindDaysBefore(3)
                .build();
    }

    @Test
    void createEmitsCreatedAudit() {
        when(billRepo.save(any()))
                .thenAnswer(
                        inv -> {
                            Bill b = inv.getArgument(0);
                            b.setId(UUID.randomUUID());
                            return b;
                        });

        service.create(
                userId,
                new CreateBillRequest(
                        "Electric", new BigDecimal("350"), 15, "utilities", 3, false, "note"));

        verify(auditService)
                .success(eq(AuditAction.BILL_CREATED), eq(userId), any(), contains("id="));
    }

    @Test
    void updateEmitsUpdatedAudit() {
        Bill existing = bill();
        when(billRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(paymentRepo.findByBillIdAndPeriod(eq(existing.getId()), any()))
                .thenReturn(Optional.empty());

        service.update(
                userId,
                existing.getId(),
                new CreateBillRequest(
                        "Electric+", new BigDecimal("400"), 15, "utilities", 3, false, "note"));

        verify(auditService)
                .success(
                        eq(AuditAction.BILL_UPDATED),
                        eq(userId),
                        any(),
                        contains(existing.getId().toString()));
    }

    @Test
    void deleteEmitsDeletedAudit() {
        Bill existing = bill();
        when(billRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.delete(userId, existing.getId());

        verify(auditService)
                .success(
                        eq(AuditAction.BILL_DELETED),
                        eq(userId),
                        any(),
                        contains(existing.getId().toString()));
    }

    @Test
    void payEmitsPaymentRecordedAudit() {
        Bill existing = bill();
        when(billRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));
        when(paymentRepo.findByBillIdAndPeriod(existing.getId(), "2026-04"))
                .thenReturn(Optional.empty());
        when(paymentRepo.findTop2ByBillIdAndStatusOrderByPeriodDesc(
                        existing.getId(), PaymentStatus.PAID))
                .thenReturn(List.of());

        service.pay(userId, existing.getId(), new PayBillRequest("2026-04", null, null));

        verify(auditService)
                .success(
                        eq(AuditAction.BILL_PAYMENT_RECORDED),
                        eq(userId),
                        any(),
                        contains("period=2026-04"));
    }
}
