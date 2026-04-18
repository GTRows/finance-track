import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { AddTransactionDialog } from '@/components/budget/AddTransactionDialog';
import { BudgetCategoriesDialog } from '@/components/budget/BudgetCategoriesDialog';
import { MonthlyLogSection } from '@/components/budget/MonthlyLogSection';
import { BudgetRulesSection } from '@/components/budget/BudgetRulesSection';
import { RecurringTemplatesSection } from '@/components/budget/RecurringTemplatesSection';
import {
  useTransactions,
  useBudgetSummary,
  useCategories,
  useCreateTransaction,
  useDeleteTransaction,
} from '@/hooks/useBudget';
import { formatTRY, formatPercent } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import {
  Wallet,
  TrendingUp,
  TrendingDown,
  PiggyBank,
  ChevronLeft,
  ChevronRight,
  Trash2,
  Download,
  Loader2,
  Tag as TagIcon,
  X,
  RotateCcw,
  FileSpreadsheet,
} from 'lucide-react';
import { reportApi } from '@/api/report.api';
import { useTags } from '@/hooks/useTags';

function currentPeriod(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function shiftMonth(period: string, delta: number): string {
  const [y, m] = period.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function BudgetPage() {
  const { t, i18n } = useTranslation();
  const [month, setMonth] = useState(currentPeriod);
  const locale = i18n.resolvedLanguage === 'tr' ? 'tr-TR' : 'en-US';

  const [tagFilter, setTagFilter] = useState<string | null>(null);

  const summaryQuery = useBudgetSummary(month);
  const txnQuery = useTransactions(month, undefined, 0, tagFilter ?? undefined);
  const catQuery = useCategories();
  const tagsQuery = useTags();
  const createTxn = useCreateTransaction(month);
  const deleteTxn = useDeleteTransaction(month);
  const [downloading, setDownloading] = useState<null | 'csv' | 'xlsx'>(null);

  const monthRange = () => {
    const [y, m] = month.split('-').map(Number);
    const from = `${month}-01`;
    const lastDay = new Date(y, m, 0).getDate();
    const to = `${month}-${String(lastDay).padStart(2, '0')}`;
    return { from, to };
  };

  const handleDownloadCsv = async () => {
    const { from, to } = monthRange();
    try {
      setDownloading('csv');
      await reportApi.downloadBudgetCsv(from, to);
    } finally {
      setDownloading(null);
    }
  };

  const handleDownloadXlsx = async () => {
    const { from, to } = monthRange();
    try {
      setDownloading('xlsx');
      await reportApi.downloadBudgetXlsx(from, to);
    } finally {
      setDownloading(null);
    }
  };

  const summary = summaryQuery.data;
  const transactions = txnQuery.data?.content ?? [];
  const incomeCategories = catQuery.data?.income ?? [];
  const expenseCategories = catQuery.data?.expense ?? [];
  const availableTags = tagsQuery.data ?? [];
  const activeTag = availableTags.find((tag) => tag.id === tagFilter) ?? null;

  const monthLabel = (() => {
    const [y, m] = month.split('-').map(Number);
    return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(new Date(y, m - 1));
  })();

  const isCurrentMonth = month === currentPeriod();

  return (
    <div className="space-y-6 max-w-[1200px]">
      {/* Header with month navigator */}
      <PageHeader
        title={t('budget.title')}
        description={t('budget.descriptionFor', { month: monthLabel })}
        actions={
          <div className="flex items-center gap-2">
            <BudgetCategoriesDialog month={month} expenseCategories={expenseCategories} />
            <button
              type="button"
              onClick={handleDownloadCsv}
              disabled={downloading !== null || transactions.length === 0}
              title={t('reports.downloadCsv')}
              className="w-9 h-9 rounded-md border border-input flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer disabled:opacity-50"
            >
              {downloading === 'csv' ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Download className="w-4 h-4" />
              )}
            </button>
            <button
              type="button"
              onClick={handleDownloadXlsx}
              disabled={downloading !== null || transactions.length === 0}
              title={t('reports.downloadXlsx')}
              className="w-9 h-9 rounded-md border border-input flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer disabled:opacity-50"
            >
              {downloading === 'xlsx' ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <FileSpreadsheet className="w-4 h-4" />
              )}
            </button>
            <AddTransactionDialog
              incomeCategories={incomeCategories}
              expenseCategories={expenseCategories}
              onSubmit={(req) => createTxn.mutate(req)}
              isPending={createTxn.isPending}
            />
          </div>
        }
      />

      {/* Month navigator */}
      <div className="flex items-center gap-3">
        <Button
          variant="outline"
          size="icon"
          className="h-8 w-8 cursor-pointer"
          onClick={() => setMonth(shiftMonth(month, -1))}
        >
          <ChevronLeft className="w-4 h-4" />
        </Button>
        <span className="text-sm font-medium min-w-[140px] text-center capitalize">
          {monthLabel}
        </span>
        <Button
          variant="outline"
          size="icon"
          className="h-8 w-8 cursor-pointer"
          disabled={isCurrentMonth}
          onClick={() => setMonth(shiftMonth(month, 1))}
        >
          <ChevronRight className="w-4 h-4" />
        </Button>
      </div>

      {/* KPI cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          label={t('budget.income')}
          value={summary?.totalIncome}
          hint={t('common.thisMonth')}
          icon={<TrendingUp className="w-5 h-5 text-emerald-400" />}
          iconBg="bg-emerald-500/10"
          valueClass="text-emerald-400"
        />
        <KpiCard
          label={t('budget.expenses')}
          value={summary?.totalExpense}
          hint={t('common.thisMonth')}
          icon={<TrendingDown className="w-5 h-5 text-red-400" />}
          iconBg="bg-red-500/10"
          valueClass="text-red-400"
        />
        <KpiCard
          label={t('budget.net')}
          value={summary?.net}
          hint={t('budget.netHint')}
          icon={<Wallet className="w-5 h-5 text-primary" />}
          iconBg="bg-primary/10"
          valueClass={summary && summary.net >= 0 ? 'text-emerald-400' : 'text-red-400'}
        />
        <KpiCard
          label={t('budget.savingsRate')}
          value={summary?.savingsRate}
          isPercent
          hint={t('budget.savingsHint')}
          icon={<PiggyBank className="w-5 h-5 text-primary" />}
          iconBg="bg-primary/10"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Transaction list */}
        <Card className="lg:col-span-2">
          <CardHeader className="pb-2 flex-row items-center justify-between gap-3 space-y-0">
            <CardTitle className="text-sm font-medium">{t('budget.recentTransactions')}</CardTitle>
            {availableTags.length > 0 && (
              <div className="flex items-center gap-1 max-w-[65%] overflow-x-auto scrollbar-thin">
                <TagIcon className="w-3.5 h-3.5 text-muted-foreground flex-shrink-0" />
                {activeTag ? (
                  <button
                    type="button"
                    onClick={() => setTagFilter(null)}
                    className="inline-flex items-center gap-1 rounded-full border border-sky-500/50 bg-sky-500/10 px-2 py-0.5 text-[11px] text-sky-300 cursor-pointer"
                  >
                    <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: activeTag.color ?? '#64748b' }} />
                    {activeTag.name}
                    <X className="w-3 h-3" />
                  </button>
                ) : (
                  availableTags.slice(0, 6).map((tag) => (
                    <button
                      key={tag.id}
                      type="button"
                      onClick={() => setTagFilter(tag.id)}
                      className="inline-flex items-center gap-1 rounded-full border border-border px-2 py-0.5 text-[11px] text-muted-foreground hover:bg-accent transition-colors cursor-pointer flex-shrink-0"
                    >
                      <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: tag.color ?? '#64748b' }} />
                      {tag.name}
                    </button>
                  ))
                )}
              </div>
            )}
          </CardHeader>
          <CardContent className="px-0">
            {transactions.length === 0 ? (
              <EmptyState
                icon={Wallet}
                title={t('budget.emptyTitle')}
                description={t('budget.emptyDesc')}
                action={
                  <AddTransactionDialog
                    incomeCategories={incomeCategories}
                    expenseCategories={expenseCategories}
                    onSubmit={(req) => createTxn.mutate(req)}
                    isPending={createTxn.isPending}
                  />
                }
              />
            ) : (
              <div className="divide-y divide-border">
                {transactions.map((txn) => (
                  <div
                    key={txn.id}
                    className="flex items-center gap-3 px-6 py-3 group hover:bg-accent/30 transition-colors"
                  >
                    {/* Category dot */}
                    <span
                      className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                      style={{ backgroundColor: txn.categoryColor ?? 'hsl(var(--muted-foreground))' }}
                    />

                    {/* Description + category + tags */}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm truncate">
                        {txn.description || txn.categoryName || t('budget.uncategorized')}
                      </p>
                      <div className="flex items-center gap-1.5 flex-wrap mt-0.5">
                        <span className="text-[11px] text-muted-foreground">
                          {new Date(txn.txnDate).toLocaleDateString(locale, {
                            day: 'numeric',
                            month: 'short',
                          })}
                          {txn.categoryName && txn.description && (
                            <span className="ml-1.5 opacity-70">
                              {txn.categoryName}
                            </span>
                          )}
                        </span>
                        {txn.tags && txn.tags.length > 0 && txn.tags.map((tag) => (
                          <span
                            key={tag.id}
                            className="inline-flex items-center gap-1 rounded-full bg-sky-500/10 border border-sky-500/30 px-1.5 py-px text-[10px] text-sky-300"
                          >
                            <span className="w-1 h-1 rounded-full" style={{ backgroundColor: tag.color ?? '#64748b' }} />
                            {tag.name}
                          </span>
                        ))}
                      </div>
                    </div>

                    {/* Amount */}
                    <span
                      className={cn(
                        'text-sm font-mono tabular-nums font-medium',
                        txn.txnType === 'INCOME' ? 'text-emerald-400' : 'text-red-400'
                      )}
                    >
                      {txn.txnType === 'INCOME' ? '+' : '-'}
                      {formatTRY(txn.amount)}
                    </span>

                    {/* Delete */}
                    <button
                      onClick={() => deleteTxn.mutate(txn.id)}
                      className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded hover:bg-destructive/10 cursor-pointer"
                      title={t('common.delete')}
                    >
                      <Trash2 className="w-3.5 h-3.5 text-destructive" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Category breakdown */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">{t('budget.byCategory')}</CardTitle>
          </CardHeader>
          <CardContent>
            {!summary || summary.expenseByCategory.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <div className="w-12 h-12 rounded-xl bg-muted flex items-center justify-center mb-4">
                  <PiggyBank className="w-6 h-6 text-muted-foreground" />
                </div>
                <p className="text-sm text-muted-foreground">{t('budget.noCategoryData')}</p>
              </div>
            ) : (
              <div className="space-y-3">
                {summary.expenseByCategory.map((cat, i) => {
                  const rollover = cat.rolloverAmount ?? 0;
                  const hasRollover = rollover > 0;
                  const effective = cat.effectiveBudget;
                  const budgetUsedPct = effective && effective > 0
                    ? Math.min(100, (cat.amount / effective) * 100)
                    : null;
                  return (
                    <div key={cat.categoryId ?? i} className="space-y-1.5">
                      <div className="flex items-center justify-between text-xs">
                        <div className="flex items-center gap-1.5 min-w-0">
                          <span
                            className="w-2 h-2 rounded-full shrink-0"
                            style={{ backgroundColor: cat.categoryColor ?? 'hsl(var(--muted-foreground))' }}
                          />
                          <span className="text-muted-foreground truncate">{cat.categoryName}</span>
                          {hasRollover && (
                            <span
                              className="flex items-center gap-1 h-5 px-1.5 rounded-full border border-sky-500/40 bg-sky-500/10 text-[10px] font-medium text-sky-300 shrink-0"
                              title={t('budget.rolloverTooltip', { amount: formatTRY(rollover) })}
                            >
                              <RotateCcw className="w-2.5 h-2.5" />
                              +{formatTRY(rollover)}
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="font-mono tabular-nums">{formatTRY(cat.amount)}</span>
                          <span className="text-muted-foreground w-10 text-right">
                            {formatPercent(cat.percent / 100)}
                          </span>
                        </div>
                      </div>
                      <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all duration-500"
                          style={{
                            width: `${Math.min(cat.percent, 100)}%`,
                            backgroundColor: cat.categoryColor ?? 'hsl(var(--primary))',
                          }}
                        />
                      </div>
                      {effective != null && effective > 0 && (
                        <div className="flex items-center justify-between text-[10px] text-muted-foreground">
                          <span>
                            {t('budget.ofEffective', {
                              used: formatTRY(cat.amount),
                              budget: formatTRY(effective),
                            })}
                          </span>
                          <span
                            className={cn(
                              'tabular-nums',
                              budgetUsedPct != null && budgetUsedPct >= 100 && 'text-red-400'
                            )}
                          >
                            {budgetUsedPct != null ? formatPercent(budgetUsedPct / 100) : ''}
                          </span>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <RecurringTemplatesSection />

      <BudgetRulesSection />

      <MonthlyLogSection currentMonth={month} />
    </div>
  );
}

function KpiCard({
  label,
  value,
  hint,
  icon,
  iconBg,
  valueClass,
  isPercent,
}: {
  label: string;
  value: number | undefined;
  hint: string;
  icon: React.ReactNode;
  iconBg: string;
  valueClass?: string;
  isPercent?: boolean;
}) {
  const formatted = value != null
    ? isPercent
      ? `%${value.toFixed(1)}`
      : formatTRY(value)
    : '--';

  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <div className="space-y-2">
            <p className="text-sm text-muted-foreground font-medium">{label}</p>
            <p className={cn('text-2xl font-semibold font-mono tabular-nums tracking-tight', valueClass)}>
              {formatted}
            </p>
            <p className="text-xs text-muted-foreground">{hint}</p>
          </div>
          <div className={cn('w-10 h-10 rounded-lg flex items-center justify-center', iconBg)}>
            {icon}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
