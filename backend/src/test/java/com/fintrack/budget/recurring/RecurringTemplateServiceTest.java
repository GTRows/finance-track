package com.fintrack.budget.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.budget.recurring.dto.RecurringTemplateResponse;
import com.fintrack.budget.recurring.dto.UpsertRecurringRequest;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.BudgetTransaction.TxnType;
import com.fintrack.common.entity.ExpenseCategory;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.entity.RecurringTemplate;
import com.fintrack.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
class RecurringTemplateServiceTest {

    @Mock RecurringTemplateRepository templateRepo;
    @Mock TransactionRepository txnRepo;
    @Mock IncomeCategoryRepository incomeRepo;
    @Mock ExpenseCategoryRepository expenseRepo;

    @InjectMocks RecurringTemplateService service;

    private final UUID userId = UUID.randomUUID();

    private RecurringTemplate template(
            TxnType type, String amount, int dom, boolean active, UUID categoryId) {
        return RecurringTemplate.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .txnType(type)
                .amount(new BigDecimal(amount))
                .categoryId(categoryId)
                .description("x")
                .dayOfMonth(dom)
                .active(active)
                .build();
    }

    @Test
    void scheduledDateForClampsToMonthLength() {
        assertThat(RecurringTemplateService.scheduledDateFor(31, YearMonth.of(2026, 2)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(RecurringTemplateService.scheduledDateFor(15, YearMonth.of(2026, 5)))
                .isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(RecurringTemplateService.scheduledDateFor(31, YearMonth.of(2024, 2)))
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void nextDueOnReturnsThisMonthWhenDayIsTodayAndNotYetMaterialized() {
        LocalDate today = LocalDate.of(2026, 4, 15);
        RecurringTemplate t =
                RecurringTemplate.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .txnType(TxnType.INCOME)
                        .amount(BigDecimal.ONE)
                        .dayOfMonth(15)
                        .active(true)
                        .build();

        assertThat(RecurringTemplateService.nextDueOn(t, today))
                .isEqualTo(LocalDate.of(2026, 4, 15));
    }

    @Test
    void nextDueOnRollsToNextMonthWhenDayInPast() {
        LocalDate today = LocalDate.of(2026, 4, 20);
        RecurringTemplate t =
                RecurringTemplate.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .txnType(TxnType.INCOME)
                        .amount(BigDecimal.ONE)
                        .dayOfMonth(10)
                        .active(true)
                        .build();

        assertThat(RecurringTemplateService.nextDueOn(t, today))
                .isEqualTo(LocalDate.of(2026, 5, 10));
    }

    @Test
    void nextDueOnRollsToNextMonthWhenAlreadyMaterializedThisMonth() {
        LocalDate today = LocalDate.of(2026, 4, 10);
        RecurringTemplate t =
                RecurringTemplate.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .txnType(TxnType.INCOME)
                        .amount(BigDecimal.ONE)
                        .dayOfMonth(15)
                        .active(true)
                        .lastMaterializedOn(LocalDate.of(2026, 4, 15))
                        .build();

        assertThat(RecurringTemplateService.nextDueOn(t, today))
                .isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void listEmptyWhenNoTemplates() {
        when(templateRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

        assertThat(service.listForUser(userId)).isEmpty();
        verify(incomeRepo, never()).findByUserIdOrderByNameAsc(any());
    }

    @Test
    void listAttachesCategoryNameAndNextDueOn() {
        IncomeCategory salary =
                IncomeCategory.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .name("Salary")
                        .build();
        RecurringTemplate t = template(TxnType.INCOME, "50000", 1, true, salary.getId());
        when(templateRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(t));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(salary));
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        List<RecurringTemplateResponse> res = service.listForUser(userId);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).categoryName()).isEqualTo("Salary");
        assertThat(res.get(0).nextDueOn()).isNotNull();
    }

    @Test
    void listPassesNullCategoryNameWhenTemplateHasNoCategory() {
        RecurringTemplate t = template(TxnType.INCOME, "50000", 1, true, null);
        when(templateRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(t));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        RecurringTemplateResponse res = service.listForUser(userId).get(0);

        assertThat(res.categoryName()).isNull();
    }

    @Test
    void createPersistsWithDefaultActiveTrueWhenNull() {
        when(templateRepo.save(any(RecurringTemplate.class)))
                .thenAnswer(
                        inv -> {
                            RecurringTemplate t = inv.getArgument(0);
                            t.setId(UUID.randomUUID());
                            return t;
                        });

        service.create(
                userId,
                new UpsertRecurringRequest(
                        TxnType.INCOME, new BigDecimal("1000"), null, "Salary", 1, null));

        ArgumentCaptor<RecurringTemplate> captor = ArgumentCaptor.forClass(RecurringTemplate.class);
        verify(templateRepo).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getDayOfMonth()).isEqualTo(1);
    }

    @Test
    void createRespectsActiveFlagWhenProvided() {
        when(templateRepo.save(any(RecurringTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(
                userId,
                new UpsertRecurringRequest(
                        TxnType.EXPENSE, new BigDecimal("200"), null, "Rent", 5, Boolean.FALSE));

        ArgumentCaptor<RecurringTemplate> captor = ArgumentCaptor.forClass(RecurringTemplate.class);
        verify(templateRepo).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void updateRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(templateRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId,
                                        id,
                                        new UpsertRecurringRequest(
                                                TxnType.INCOME,
                                                BigDecimal.ONE,
                                                null,
                                                "x",
                                                1,
                                                null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMutatesAllFieldsButKeepsActiveWhenRequestActiveNull() {
        RecurringTemplate existing = template(TxnType.INCOME, "100", 1, true, null);
        when(templateRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.update(
                userId,
                existing.getId(),
                new UpsertRecurringRequest(
                        TxnType.EXPENSE, new BigDecimal("250"), null, "new", 10, null));

        assertThat(existing.getTxnType()).isEqualTo(TxnType.EXPENSE);
        assertThat(existing.getAmount()).isEqualByComparingTo("250");
        assertThat(existing.getDescription()).isEqualTo("new");
        assertThat(existing.getDayOfMonth()).isEqualTo(10);
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void updateAppliesActiveWhenRequestActiveProvided() {
        RecurringTemplate existing = template(TxnType.INCOME, "100", 1, true, null);
        when(templateRepo.findByIdAndUserId(existing.getId(), userId))
                .thenReturn(Optional.of(existing));

        service.update(
                userId,
                existing.getId(),
                new UpsertRecurringRequest(
                        TxnType.INCOME, BigDecimal.TEN, null, null, 1, Boolean.FALSE));

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void deleteRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(templateRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepo, never()).delete(any());
    }

    @Test
    void deleteRemovesWhenOwned() {
        RecurringTemplate t = template(TxnType.INCOME, "100", 1, true, null);
        when(templateRepo.findByIdAndUserId(t.getId(), userId)).thenReturn(Optional.of(t));

        service.delete(userId, t.getId());

        verify(templateRepo).delete(t);
    }

    @Test
    void materializeCreatesTransactionAndStampsLastMaterialized() {
        RecurringTemplate t = template(TxnType.EXPENSE, "150", 5, true, null);
        LocalDate on = LocalDate.of(2026, 4, 5);

        service.materialize(t, on);

        ArgumentCaptor<BudgetTransaction> captor = ArgumentCaptor.forClass(BudgetTransaction.class);
        verify(txnRepo).save(captor.capture());
        BudgetTransaction saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTxnType()).isEqualTo(TxnType.EXPENSE);
        assertThat(saved.getAmount()).isEqualByComparingTo("150");
        assertThat(saved.getTxnDate()).isEqualTo(on);
        assertThat(saved.isRecurring()).isTrue();
        assertThat(t.getLastMaterializedOn()).isEqualTo(on);
    }

    @Test
    void runNowRequiresOwnership() {
        UUID id = UUID.randomUUID();
        when(templateRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runNow(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void runNowMaterializesTodayAndReturnsResponse() {
        ExpenseCategory cat =
                ExpenseCategory.builder().id(UUID.randomUUID()).userId(userId).name("Rent").build();
        RecurringTemplate t = template(TxnType.EXPENSE, "1000", 5, true, cat.getId());
        when(templateRepo.findByIdAndUserId(t.getId(), userId)).thenReturn(Optional.of(t));
        when(incomeRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(cat));

        RecurringTemplateResponse res = service.runNow(userId, t.getId());

        verify(txnRepo).save(any(BudgetTransaction.class));
        assertThat(t.getLastMaterializedOn()).isEqualTo(LocalDate.now());
        assertThat(res.categoryName()).isEqualTo("Rent");
    }
}
