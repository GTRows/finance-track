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
import com.fintrack.common.entity.*;
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
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports all user-scoped data as a JSON payload and restores it on upload. Restore wipes the
 * caller's existing data and replaces it with the payload contents; UUIDs are preserved so
 * intra-backup references stay intact.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    public static final int CURRENT_VERSION = 1;

    private final UserRepository userRepo;
    private final UserSettingsRepository settingsRepo;
    private final PortfolioRepository portfolioRepo;
    private final HoldingRepository holdingRepo;
    private final InvestmentTransactionRepository invTxnRepo;
    private final SnapshotRepository snapshotRepo;
    private final AllocationTargetRepository allocationRepo;
    private final IncomeCategoryRepository incomeCatRepo;
    private final ExpenseCategoryRepository expenseCatRepo;
    private final TagRepository tagRepo;
    private final TransactionRepository txnRepo;
    private final TransactionTagRepository txnTagRepo;
    private final RecurringTemplateRepository recurringRepo;
    private final TransactionCategoryRuleRepository categoryRuleRepo;
    private final BudgetRuleRepository budgetRuleRepo;
    private final BillRepository billRepo;
    private final BillPaymentRepository billPaymentRepo;
    private final SavingsGoalRepository goalRepo;
    private final SavingsContributionRepository contributionRepo;
    private final DebtRepository debtRepo;
    private final DebtPaymentRepository debtPaymentRepo;
    private final NetWorthEventRepository netWorthRepo;

    @PersistenceContext private EntityManager entityManager;

    @Transactional(readOnly = true)
    public BackupPayload export(UUID userId) {
        User user =
                userRepo.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserSettings settings = settingsRepo.findById(userId).orElse(null);

        List<Portfolio> portfolios = portfolioRepo.findByUserIdOrderByCreatedAtAsc(userId);
        List<UUID> portfolioIds = portfolios.stream().map(Portfolio::getId).toList();

        List<PortfolioHolding> holdings = new ArrayList<>();
        List<InvestmentTransaction> invTxns = new ArrayList<>();
        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        List<PortfolioAllocationTarget> allocations = new ArrayList<>();
        for (UUID pid : portfolioIds) {
            holdings.addAll(holdingRepo.findByPortfolioId(pid));
            invTxns.addAll(invTxnRepo.findByPortfolioIdOrderByTxnDateDescCreatedAtDesc(pid));
            snapshots.addAll(snapshotRepo.findByPortfolioIdOrderBySnapshotDateAsc(pid));
            allocations.addAll(allocationRepo.findByPortfolioId(pid));
        }

        List<IncomeCategory> incomeCats = incomeCatRepo.findByUserIdOrderByNameAsc(userId);
        List<ExpenseCategory> expenseCats = expenseCatRepo.findByUserIdOrderByNameAsc(userId);
        List<Tag> tags = tagRepo.findByUserIdOrderByNameAsc(userId);

        List<BudgetTransaction> transactions = txnRepo.findByUserIdOrderByTxnDateAsc(userId);
        List<UUID> txnIds = transactions.stream().map(BudgetTransaction::getId).toList();
        List<TransactionTag> transactionTags =
                txnIds.isEmpty()
                        ? Collections.emptyList()
                        : txnTagRepo.findByTransactionIds(txnIds);

        List<RecurringTemplate> recurring = recurringRepo.findByUserIdOrderByCreatedAtAsc(userId);
        List<TransactionCategoryRule> categoryRules =
                categoryRuleRepo.findByUserIdOrderByPriorityAscCreatedAtAsc(userId);
        List<BudgetRule> budgetRules = budgetRuleRepo.findByUserIdOrderByCreatedAtDesc(userId);

        List<Bill> bills = billRepo.findByUserIdOrderByDueDayAsc(userId);
        List<BillPayment> billPayments = new ArrayList<>();
        for (Bill bill : bills) {
            billPayments.addAll(billPaymentRepo.findByBillIdOrderByPeriodDesc(bill.getId()));
        }

        List<SavingsGoal> goals = goalRepo.findByUserIdOrderByCreatedAtAsc(userId);
        List<SavingsGoalContribution> contributions = new ArrayList<>();
        for (SavingsGoal goal : goals) {
            contributions.addAll(
                    contributionRepo.findByGoalIdOrderByContributionDateDesc(goal.getId()));
        }

        List<Debt> debts = debtRepo.findByUserIdOrderByCreatedAtAsc(userId);
        List<DebtPayment> debtPayments = new ArrayList<>();
        for (Debt debt : debts) {
            debtPayments.addAll(debtPaymentRepo.findByDebtIdOrderByPaymentDateAsc(debt.getId()));
        }

        List<NetWorthEvent> netWorthEvents = netWorthRepo.findByUserIdOrderByEventDateDesc(userId);

        BackupPayload.BackupMeta meta =
                new BackupPayload.BackupMeta(CURRENT_VERSION, Instant.now(), user.getEmail());

        log.info(
                "Backup exported: userId={} transactions={} portfolios={} bills={} goals={}"
                        + " debts={}",
                userId,
                transactions.size(),
                portfolios.size(),
                bills.size(),
                goals.size(),
                debts.size());

        return new BackupPayload(
                meta,
                settings,
                portfolios,
                holdings,
                invTxns,
                snapshots,
                allocations,
                incomeCats,
                expenseCats,
                tags,
                transactions,
                transactionTags,
                recurring,
                categoryRules,
                budgetRules,
                bills,
                billPayments,
                goals,
                contributions,
                debts,
                debtPayments,
                netWorthEvents);
    }

    @Transactional
    public void restore(UUID userId, BackupPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Backup payload is required");
        }
        if (payload.meta() == null || payload.meta().version() != CURRENT_VERSION) {
            int got = payload.meta() == null ? -1 : payload.meta().version();
            throw new IllegalArgumentException(
                    "Unsupported backup version: got " + got + ", expected " + CURRENT_VERSION);
        }

        wipe(userId);

        if (payload.userSettings() != null) {
            payload.userSettings().setUserId(userId);
            entityManager.merge(payload.userSettings());
        }

        persistAll(payload.portfolios(), userId, Portfolio::setUserId);
        persistAll(payload.holdings(), null, null);
        persistAll(payload.investmentTransactions(), null, null);
        persistAll(payload.portfolioSnapshots(), null, null);
        persistAll(payload.allocationTargets(), null, null);

        persistAll(payload.incomeCategories(), userId, IncomeCategory::setUserId);
        persistAll(payload.expenseCategories(), userId, ExpenseCategory::setUserId);

        persistAll(payload.tags(), userId, Tag::setUserId);

        persistAll(payload.transactions(), userId, BudgetTransaction::setUserId);
        persistAll(payload.transactionTags(), null, null);

        persistAll(payload.recurringTemplates(), userId, RecurringTemplate::setUserId);
        persistAll(payload.categoryRules(), userId, TransactionCategoryRule::setUserId);
        persistAll(payload.budgetRules(), userId, BudgetRule::setUserId);

        persistAll(payload.bills(), userId, Bill::setUserId);
        persistAll(payload.billPayments(), null, null);

        persistAll(payload.savingsGoals(), userId, SavingsGoal::setUserId);
        persistAll(payload.savingsContributions(), null, null);

        persistAll(payload.debts(), userId, Debt::setUserId);
        persistAll(payload.debtPayments(), null, null);

        persistAll(payload.netWorthEvents(), userId, NetWorthEvent::setUserId);

        entityManager.flush();
        log.info("Backup restored: userId={}", userId);
    }

    private <T> void persistAll(
            List<T> items, UUID userId, java.util.function.BiConsumer<T, UUID> ownerSetter) {
        if (items == null) return;
        for (T item : items) {
            if (ownerSetter != null && userId != null) {
                ownerSetter.accept(item, userId);
            }
            entityManager.persist(item);
        }
    }

    /**
     * Deletes every user-scoped row in FK-safe order. Uses JPQL bulk deletes so we never pull large
     * result sets into memory during restore.
     */
    private void wipe(UUID userId) {
        exec(
                "DELETE FROM TransactionTag tt WHERE tt.transactionId IN "
                        + "(SELECT t.id FROM BudgetTransaction t WHERE t.userId = :u)",
                userId);

        exec(
                "DELETE FROM BillPayment bp WHERE bp.billId IN "
                        + "(SELECT b.id FROM Bill b WHERE b.userId = :u)",
                userId);
        exec("DELETE FROM Bill b WHERE b.userId = :u", userId);

        exec(
                "DELETE FROM SavingsGoalContribution c WHERE c.goalId IN "
                        + "(SELECT g.id FROM SavingsGoal g WHERE g.userId = :u)",
                userId);
        exec("DELETE FROM SavingsGoal g WHERE g.userId = :u", userId);

        exec(
                "DELETE FROM DebtPayment dp WHERE dp.debtId IN "
                        + "(SELECT d.id FROM Debt d WHERE d.userId = :u)",
                userId);
        exec("DELETE FROM Debt d WHERE d.userId = :u", userId);

        exec("DELETE FROM NetWorthEvent e WHERE e.userId = :u", userId);

        exec("DELETE FROM BudgetRule r WHERE r.userId = :u", userId);
        exec("DELETE FROM TransactionCategoryRule r WHERE r.userId = :u", userId);
        exec("DELETE FROM RecurringTemplate r WHERE r.userId = :u", userId);

        exec("DELETE FROM BudgetTransaction t WHERE t.userId = :u", userId);

        exec("DELETE FROM Tag t WHERE t.userId = :u", userId);
        exec("DELETE FROM ExpenseCategory c WHERE c.userId = :u", userId);
        exec("DELETE FROM IncomeCategory c WHERE c.userId = :u", userId);

        exec(
                "DELETE FROM PortfolioAllocationTarget a WHERE a.portfolioId IN "
                        + "(SELECT p.id FROM Portfolio p WHERE p.userId = :u)",
                userId);
        exec(
                "DELETE FROM PortfolioSnapshot s WHERE s.portfolioId IN "
                        + "(SELECT p.id FROM Portfolio p WHERE p.userId = :u)",
                userId);
        exec(
                "DELETE FROM InvestmentTransaction it WHERE it.portfolioId IN "
                        + "(SELECT p.id FROM Portfolio p WHERE p.userId = :u)",
                userId);
        exec(
                "DELETE FROM PortfolioHolding h WHERE h.portfolioId IN "
                        + "(SELECT p.id FROM Portfolio p WHERE p.userId = :u)",
                userId);
        exec("DELETE FROM Portfolio p WHERE p.userId = :u", userId);

        entityManager.flush();
        entityManager.clear();
    }

    private void exec(String jpql, UUID userId) {
        entityManager.createQuery(jpql).setParameter("u", userId).executeUpdate();
    }
}
