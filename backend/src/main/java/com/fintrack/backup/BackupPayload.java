package com.fintrack.backup;

import com.fintrack.common.entity.*;

import java.time.Instant;
import java.util.List;

/**
 * Versioned envelope for a user's exported data. Each field is a straight list
 * of entities; cross-references are preserved as UUIDs.
 */
public record BackupPayload(
        BackupMeta meta,
        UserSettings userSettings,
        List<Portfolio> portfolios,
        List<PortfolioHolding> holdings,
        List<InvestmentTransaction> investmentTransactions,
        List<PortfolioSnapshot> portfolioSnapshots,
        List<PortfolioAllocationTarget> allocationTargets,
        List<IncomeCategory> incomeCategories,
        List<ExpenseCategory> expenseCategories,
        List<Tag> tags,
        List<BudgetTransaction> transactions,
        List<TransactionTag> transactionTags,
        List<RecurringTemplate> recurringTemplates,
        List<TransactionCategoryRule> categoryRules,
        List<BudgetRule> budgetRules,
        List<Bill> bills,
        List<BillPayment> billPayments,
        List<SavingsGoal> savingsGoals,
        List<SavingsGoalContribution> savingsContributions,
        List<Debt> debts,
        List<DebtPayment> debtPayments,
        List<NetWorthEvent> netWorthEvents
) {
    public record BackupMeta(int version, Instant exportedAt, String userEmail) {}
}
