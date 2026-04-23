package com.fintrack.backup;

import com.fintrack.auth.UserRepository;
import com.fintrack.bills.BillPaymentRepository;
import com.fintrack.bills.BillRepository;
import com.fintrack.budget.BudgetRuleRepository;
import com.fintrack.budget.ExpenseCategoryRepository;
import com.fintrack.budget.IncomeCategoryRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.budget.recurring.RecurringTemplateRepository;
import com.fintrack.budget.rule.TransactionCategoryRuleRepository;
import com.fintrack.common.entity.Bill;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.Debt;
import com.fintrack.common.entity.IncomeCategory;
import com.fintrack.common.entity.Portfolio;
import com.fintrack.common.entity.SavingsGoal;
import com.fintrack.common.entity.Tag;
import com.fintrack.common.entity.User;
import com.fintrack.common.entity.UserSettings;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.debt.DebtPaymentRepository;
import com.fintrack.debt.DebtRepository;
import com.fintrack.networth.NetWorthEventRepository;
import com.fintrack.portfolio.PortfolioRepository;
import com.fintrack.portfolio.allocation.AllocationTargetRepository;
import com.fintrack.portfolio.holding.HoldingRepository;
import com.fintrack.portfolio.snapshot.SnapshotRepository;
import com.fintrack.portfolio.transaction.InvestmentTransactionRepository;
import com.fintrack.savings.SavingsContributionRepository;
import com.fintrack.savings.SavingsGoalRepository;
import com.fintrack.settings.UserSettingsRepository;
import com.fintrack.tag.TagRepository;
import com.fintrack.tag.TransactionTagRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock UserRepository userRepo;
    @Mock UserSettingsRepository settingsRepo;
    @Mock PortfolioRepository portfolioRepo;
    @Mock HoldingRepository holdingRepo;
    @Mock InvestmentTransactionRepository invTxnRepo;
    @Mock SnapshotRepository snapshotRepo;
    @Mock AllocationTargetRepository allocationRepo;
    @Mock IncomeCategoryRepository incomeCatRepo;
    @Mock ExpenseCategoryRepository expenseCatRepo;
    @Mock TagRepository tagRepo;
    @Mock TransactionRepository txnRepo;
    @Mock TransactionTagRepository txnTagRepo;
    @Mock RecurringTemplateRepository recurringRepo;
    @Mock TransactionCategoryRuleRepository categoryRuleRepo;
    @Mock BudgetRuleRepository budgetRuleRepo;
    @Mock BillRepository billRepo;
    @Mock BillPaymentRepository billPaymentRepo;
    @Mock SavingsGoalRepository goalRepo;
    @Mock SavingsContributionRepository contributionRepo;
    @Mock DebtRepository debtRepo;
    @Mock DebtPaymentRepository debtPaymentRepo;
    @Mock NetWorthEventRepository netWorthRepo;
    @Mock EntityManager entityManager;

    @InjectMocks BackupService service;

    private final UUID userId = UUID.randomUUID();

    private User user() {
        return User.builder().id(userId).username("ali").email("ali@x").password("pw").build();
    }

    private void stubEmptyRepos() {
        when(portfolioRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(tagRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(txnRepo.findByUserIdOrderByTxnDateAsc(userId)).thenReturn(List.of());
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(categoryRuleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId)).thenReturn(List.of());
        when(budgetRuleRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdOrderByDueDayAsc(userId)).thenReturn(List.of());
        when(goalRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(debtRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(netWorthRepo.findByUserIdOrderByEventDateDesc(userId)).thenReturn(List.of());
    }

    @Test
    void exportThrowsWhenUserMissing() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.export(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exportReturnsEmptyCollectionsWhenNoData() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user()));
        when(settingsRepo.findById(userId)).thenReturn(Optional.empty());
        stubEmptyRepos();

        BackupPayload res = service.export(userId);

        assertThat(res.meta().version()).isEqualTo(BackupService.CURRENT_VERSION);
        assertThat(res.meta().userEmail()).isEqualTo("ali@x");
        assertThat(res.userSettings()).isNull();
        assertThat(res.portfolios()).isEmpty();
        assertThat(res.bills()).isEmpty();
        assertThat(res.transactions()).isEmpty();
        assertThat(res.savingsGoals()).isEmpty();
        assertThat(res.debts()).isEmpty();
    }

    @Test
    void exportIncludesUserSettingsWhenPresent() {
        User u = user();
        UserSettings settings = UserSettings.builder()
                .userId(userId).currency("USD").language("en").theme("light").timezone("UTC")
                .build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(settingsRepo.findById(userId)).thenReturn(Optional.of(settings));
        stubEmptyRepos();

        BackupPayload res = service.export(userId);

        assertThat(res.userSettings()).isSameAs(settings);
    }

    @Test
    void exportWalksPortfolioCollectionsPerPortfolio() {
        Portfolio p = Portfolio.builder().id(UUID.randomUUID()).userId(userId).name("P").active(true).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user()));
        when(settingsRepo.findById(userId)).thenReturn(Optional.empty());
        when(portfolioRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(p));
        when(holdingRepo.findByPortfolioId(p.getId())).thenReturn(List.of());
        when(invTxnRepo.findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(p.getId())).thenReturn(List.of());
        when(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(p.getId())).thenReturn(List.of());
        when(allocationRepo.findByPortfolioId(p.getId())).thenReturn(List.of());
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(tagRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(txnRepo.findByUserIdOrderByTxnDateAsc(userId)).thenReturn(List.of());
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(categoryRuleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId)).thenReturn(List.of());
        when(budgetRuleRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdOrderByDueDayAsc(userId)).thenReturn(List.of());
        when(goalRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(debtRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(netWorthRepo.findByUserIdOrderByEventDateDesc(userId)).thenReturn(List.of());

        BackupPayload res = service.export(userId);

        assertThat(res.portfolios()).containsExactly(p);
        verify(holdingRepo).findByPortfolioId(p.getId());
        verify(invTxnRepo).findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(p.getId());
    }

    @Test
    void exportPullsTagsOnlyWhenTransactionsExist() {
        User u = user();
        BudgetTransaction txn = BudgetTransaction.builder()
                .id(UUID.randomUUID()).userId(userId)
                .txnType(BudgetTransaction.TxnType.EXPENSE)
                .amount(new BigDecimal("10")).txnDate(LocalDate.now()).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(settingsRepo.findById(userId)).thenReturn(Optional.empty());
        when(portfolioRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(incomeCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(expenseCatRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(tagRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
        when(txnRepo.findByUserIdOrderByTxnDateAsc(userId)).thenReturn(List.of(txn));
        when(txnTagRepo.findByTransactionIds(any())).thenReturn(List.of());
        when(recurringRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(categoryRuleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId)).thenReturn(List.of());
        when(budgetRuleRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(billRepo.findByUserIdOrderByDueDayAsc(userId)).thenReturn(List.of());
        when(goalRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(debtRepo.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(netWorthRepo.findByUserIdOrderByEventDateDesc(userId)).thenReturn(List.of());

        service.export(userId);

        verify(txnTagRepo).findByTransactionIds(any());
    }

    @Test
    void restoreRejectsNullPayload() {
        assertThatThrownBy(() -> service.restore(userId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreRejectsUnsupportedVersion() {
        BackupPayload wrongVersion = new BackupPayload(
                new BackupPayload.BackupMeta(99, Instant.now(), "x"),
                null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());

        assertThatThrownBy(() -> service.restore(userId, wrongVersion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void restoreRejectsMissingMeta() {
        BackupPayload noMeta = new BackupPayload(
                null,
                null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());

        assertThatThrownBy(() -> service.restore(userId, noMeta))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreWipesExistingDataBeforePersistingPayload() {
        Query query = org.mockito.Mockito.mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        BackupPayload empty = new BackupPayload(
                new BackupPayload.BackupMeta(BackupService.CURRENT_VERSION, Instant.now(), "x"),
                null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());

        service.restore(userId, empty);

        verify(entityManager, atLeastOnce()).createQuery(anyString());
        verify(entityManager, atLeastOnce()).flush();
        verify(entityManager).clear();
    }

    @Test
    void restoreReassignsUserIdOnOwnedEntities() {
        Query query = org.mockito.Mockito.mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        UUID differentUser = UUID.randomUUID();
        Bill bill = Bill.builder().id(UUID.randomUUID()).userId(differentUser)
                .name("X").amount(BigDecimal.TEN).dueDay(1).active(true).build();
        Tag tag = Tag.builder().id(UUID.randomUUID()).userId(differentUser).name("t").build();
        IncomeCategory cat = IncomeCategory.builder().id(UUID.randomUUID()).userId(differentUser).name("c").build();
        SavingsGoal goal = SavingsGoal.builder().id(UUID.randomUUID()).userId(differentUser)
                .name("g").targetAmount(BigDecimal.TEN).build();
        Debt debt = Debt.builder().id(UUID.randomUUID()).userId(differentUser)
                .name("d").debtType("LOAN").principal(BigDecimal.TEN)
                .annualRate(BigDecimal.ZERO).termMonths(1).startDate(LocalDate.now()).build();

        BackupPayload payload = new BackupPayload(
                new BackupPayload.BackupMeta(BackupService.CURRENT_VERSION, Instant.now(), "x"),
                null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(cat), List.of(), List.of(tag), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                List.of(bill), List.of(),
                List.of(goal), List.of(),
                List.of(debt), List.of(),
                List.of());

        service.restore(userId, payload);

        assertThat(bill.getUserId()).isEqualTo(userId);
        assertThat(tag.getUserId()).isEqualTo(userId);
        assertThat(cat.getUserId()).isEqualTo(userId);
        assertThat(goal.getUserId()).isEqualTo(userId);
        assertThat(debt.getUserId()).isEqualTo(userId);
        verify(entityManager, atLeastOnce()).persist(any());
    }

    @Test
    void restoreNullOwnerCollectionSkipsPersist() {
        Query query = org.mockito.Mockito.mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        BackupPayload payload = new BackupPayload(
                new BackupPayload.BackupMeta(BackupService.CURRENT_VERSION, Instant.now(), "x"),
                null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        service.restore(userId, payload);

        verify(entityManager, never()).persist(any());
        verify(entityManager, atLeastOnce()).flush();
    }
}
