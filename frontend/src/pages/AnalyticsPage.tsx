import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  Legend,
} from 'recharts';
import { Loader2, TrendingUp, PiggyBank, Activity, LineChart as LineIcon, ArrowUpRight, Coins } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { Link } from 'react-router-dom';
import { useMonthlySummaries } from '@/hooks/useBudget';
import { usePortfolios } from '@/hooks/usePortfolios';
import { usePortfolioSnapshotsAggregate } from '@/hooks/useAnalytics';
import { CashFlowProjectionChart } from '@/components/analytics/CashFlowProjectionChart';
import { BenchmarkOverlayChart } from '@/components/analytics/BenchmarkOverlayChart';
import { formatMonth, formatPercent, formatShortDate, formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import {
  DateRangePicker,
  defaultDateRange,
  filterByRange,
  type DateRange,
} from '@/components/ui/date-range-picker';

function compactTRY(value: number): string {
  if (value === 0) return '0 ₺';
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B ₺`;
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M ₺`;
  if (abs >= 1_000) return `${(value / 1_000).toFixed(0)}K ₺`;
  return `${value.toFixed(0)} ₺`;
}

function yearsBetween(startIso: string, endIso: string): number {
  const ms = new Date(endIso).getTime() - new Date(startIso).getTime();
  return ms / (1000 * 60 * 60 * 24 * 365.25);
}

export function AnalyticsPage() {
  const { t } = useTranslation();
  const [range, setRange] = useState<DateRange>(() => defaultDateRange());
  const summariesQuery = useMonthlySummaries();
  const portfoliosQuery = usePortfolios();
  const snapshots = usePortfolioSnapshotsAggregate(portfoliosQuery.data);

  const sortedSummaries = useMemo(() => {
    return (summariesQuery.data ?? [])
      .slice()
      .sort((a, b) => a.period.localeCompare(b.period));
  }, [summariesQuery.data]);

  const budgetSeries = useMemo(() => {
    const mapped = sortedSummaries.map((s) => ({
      period: s.period,
      periodLabel: formatMonth(s.period),
      income: s.totalIncome,
      expense: s.totalExpense,
      net: s.net,
      savingsRate: s.savingsRate,
    }));
    return filterByRange(mapped, (row) => `${row.period}-01`, range);
  }, [sortedSummaries, range]);

  const expenseGrowth = useMemo(() => {
    if (budgetSeries.length < 2) return null;
    const first = budgetSeries[0].expense;
    const last = budgetSeries[budgetSeries.length - 1].expense;
    if (first === 0) return null;
    return (last - first) / first;
  }, [budgetSeries]);

  const avgSavingsRate = useMemo(() => {
    if (budgetSeries.length === 0) return null;
    const total = budgetSeries.reduce((acc, b) => acc + b.savingsRate, 0);
    return total / budgetSeries.length;
  }, [budgetSeries]);

  const portfolioSeries = useMemo(() => {
    const mapped = snapshots.data.map((p) => ({
      ...p,
      dateLabel: formatShortDate(p.date),
    }));
    return filterByRange(mapped, (row) => row.date, range);
  }, [snapshots.data, range]);

  const cagr = useMemo(() => {
    if (portfolioSeries.length < 2) return null;
    const first = portfolioSeries[0];
    const last = portfolioSeries[portfolioSeries.length - 1];
    if (first.totalValueTry <= 0) return null;
    const years = yearsBetween(first.date, last.date);
    if (years <= 0) return null;
    return Math.pow(last.totalValueTry / first.totalValueTry, 1 / years) - 1;
  }, [portfolioSeries]);

  const isLoading =
    summariesQuery.isLoading || portfoliosQuery.isLoading || snapshots.isLoading;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-24">
        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const hasBudget = budgetSeries.length >= 2;
  const hasPortfolio = portfolioSeries.length >= 2;

  return (
    <div className="space-y-6 max-w-[1200px]">
      <PageHeader
        title={t('analytics.title')}
        description={t('analytics.description')}
        actions={<DateRangePicker value={range} onChange={setRange} />}
      />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          icon={PiggyBank}
          label={t('analytics.avgSavingsRate')}
          value={
            avgSavingsRate != null
              ? `%${(avgSavingsRate).toFixed(1)}`
              : '--'
          }
          hint={t('analytics.avgSavingsRateHint', {
            count: budgetSeries.length,
          })}
        />
        <StatCard
          icon={Activity}
          label={t('analytics.expenseGrowth')}
          value={expenseGrowth != null ? formatPercent(expenseGrowth) : '--'}
          hint={
            budgetSeries.length >= 2
              ? t('analytics.expenseGrowthHint', {
                  from: formatMonth(budgetSeries[0].period),
                  to: formatMonth(budgetSeries[budgetSeries.length - 1].period),
                })
              : t('analytics.expenseGrowthEmpty')
          }
          tone={
            expenseGrowth == null
              ? undefined
              : expenseGrowth > 0
                ? 'negative'
                : 'positive'
          }
        />
        <StatCard
          icon={TrendingUp}
          label={t('analytics.portfolioCagr')}
          value={cagr != null ? formatPercent(cagr) : '--'}
          hint={
            portfolioSeries.length >= 2
              ? t('analytics.portfolioCagrHint', {
                  from: formatShortDate(portfolioSeries[0].date),
                  to: formatShortDate(
                    portfolioSeries[portfolioSeries.length - 1].date
                  ),
                })
              : t('analytics.portfolioCagrEmpty')
          }
          tone={cagr == null ? undefined : cagr >= 0 ? 'positive' : 'negative'}
        />
        <StatCard
          icon={LineIcon}
          label={t('analytics.monthsTracked')}
          value={String(budgetSeries.length)}
          hint={
            portfoliosQuery.data
              ? t('analytics.portfoliosCount', {
                  count: portfoliosQuery.data.length,
                })
              : '--'
          }
        />
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium">
            {t('analytics.savingsRateTrend')}
          </CardTitle>
          <CardDescription className="text-xs">
            {t('analytics.savingsRateTrendHint')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {!hasBudget ? (
            <EmptyState
              icon={PiggyBank}
              title={t('analytics.emptyBudgetTitle')}
              description={t('analytics.emptyBudgetDesc')}
            />
          ) : (
            <div className="h-[260px] w-full -ml-2">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={budgetSeries} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="savingsGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="hsl(172 70% 50%)" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="hsl(172 70% 50%)" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid
                    stroke="hsl(var(--border))"
                    strokeDasharray="2 4"
                    vertical={false}
                    opacity={0.4}
                  />
                  <XAxis
                    dataKey="periodLabel"
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    minTickGap={32}
                  />
                  <YAxis
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    width={50}
                    tickFormatter={(v: number) => `%${v.toFixed(0)}`}
                  />
                  <Tooltip
                    cursor={{
                      stroke: 'hsl(var(--border))',
                      strokeWidth: 1,
                      strokeDasharray: '2 4',
                    }}
                    contentStyle={{
                      backgroundColor: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: '6px',
                      fontSize: '12px',
                    }}
                    formatter={(v: number) => [`%${v.toFixed(1)}`, t('analytics.savingsRate')]}
                  />
                  <Area
                    type="monotone"
                    dataKey="savingsRate"
                    stroke="hsl(172 70% 50%)"
                    strokeWidth={2}
                    fill="url(#savingsGradient)"
                    isAnimationActive
                    animationDuration={500}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium">
            {t('analytics.incomeExpense')}
          </CardTitle>
          <CardDescription className="text-xs">
            {t('analytics.incomeExpenseHint')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {!hasBudget ? (
            <EmptyState
              icon={Activity}
              title={t('analytics.emptyBudgetTitle')}
              description={t('analytics.emptyBudgetDesc')}
            />
          ) : (
            <div className="h-[260px] w-full -ml-2">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={budgetSeries} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid
                    stroke="hsl(var(--border))"
                    strokeDasharray="2 4"
                    vertical={false}
                    opacity={0.4}
                  />
                  <XAxis
                    dataKey="periodLabel"
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    minTickGap={32}
                  />
                  <YAxis
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    width={64}
                    tickFormatter={compactTRY}
                  />
                  <Tooltip
                    cursor={{ fill: 'hsl(var(--accent))', opacity: 0.3 }}
                    contentStyle={{
                      backgroundColor: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: '6px',
                      fontSize: '12px',
                    }}
                    formatter={(v: number) => formatTRY(v)}
                  />
                  <Legend
                    verticalAlign="top"
                    height={28}
                    iconType="circle"
                    iconSize={8}
                    wrapperStyle={{ fontSize: '11px', color: 'hsl(var(--muted-foreground))' }}
                  />
                  <Bar
                    dataKey="income"
                    name={t('analytics.income')}
                    fill="hsl(172 70% 50%)"
                    radius={[3, 3, 0, 0]}
                  />
                  <Bar
                    dataKey="expense"
                    name={t('analytics.expense')}
                    fill="hsl(0 75% 60%)"
                    radius={[3, 3, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <CashFlowProjectionChart />

      <Link
        to="/reports/capital-gains"
        className="group block rounded-lg border border-border bg-card hover:border-primary/40 transition-colors"
      >
        <div className="flex items-center gap-4 p-5">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
            <Coins className="w-5 h-5 text-primary" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium">{t('capitalGains.title')}</p>
            <p className="text-xs text-muted-foreground mt-0.5">{t('capitalGains.description')}</p>
          </div>
          <ArrowUpRight className="w-4 h-4 text-muted-foreground group-hover:text-primary transition-colors flex-shrink-0" />
        </div>
      </Link>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium">
            {t('analytics.netWorthTrend')}
          </CardTitle>
          <CardDescription className="text-xs">
            {t('analytics.netWorthTrendHint')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {!hasPortfolio ? (
            <EmptyState
              icon={TrendingUp}
              title={t('analytics.emptyPortfolioTitle')}
              description={t('analytics.emptyPortfolioDesc')}
            />
          ) : (
            <div className="h-[280px] w-full -ml-2">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={portfolioSeries} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid
                    stroke="hsl(var(--border))"
                    strokeDasharray="2 4"
                    vertical={false}
                    opacity={0.4}
                  />
                  <XAxis
                    dataKey="dateLabel"
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    minTickGap={40}
                  />
                  <YAxis
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    width={64}
                    tickFormatter={compactTRY}
                  />
                  <Tooltip
                    cursor={{
                      stroke: 'hsl(var(--border))',
                      strokeWidth: 1,
                      strokeDasharray: '2 4',
                    }}
                    contentStyle={{
                      backgroundColor: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: '6px',
                      fontSize: '12px',
                    }}
                    formatter={(v: number, key: string) => [
                      formatTRY(v),
                      key === 'totalValueTry' ? t('analytics.value') : t('analytics.cost'),
                    ]}
                  />
                  <Legend
                    verticalAlign="top"
                    height={28}
                    iconType="circle"
                    iconSize={8}
                    wrapperStyle={{ fontSize: '11px', color: 'hsl(var(--muted-foreground))' }}
                  />
                  <Line
                    type="monotone"
                    dataKey="totalValueTry"
                    name={t('analytics.value')}
                    stroke="hsl(172 70% 50%)"
                    strokeWidth={2}
                    dot={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="totalCostTry"
                    name={t('analytics.cost')}
                    stroke="hsl(var(--muted-foreground))"
                    strokeWidth={1.5}
                    strokeDasharray="4 3"
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <BenchmarkOverlayChart snapshots={snapshots.data} />
    </div>
  );
}

interface StatCardProps {
  icon: React.ElementType;
  label: string;
  value: string;
  hint: string;
  tone?: 'positive' | 'negative';
}

function StatCard({ icon: Icon, label, value, hint, tone }: StatCardProps) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <div className="space-y-2 min-w-0">
            <p className="text-sm text-muted-foreground font-medium">{label}</p>
            <p
              className={cn(
                'text-2xl font-semibold font-mono tracking-tight tabular-nums truncate',
                tone === 'positive' && 'text-positive',
                tone === 'negative' && 'text-negative'
              )}
            >
              {value}
            </p>
            <p className="text-xs text-muted-foreground">{hint}</p>
          </div>
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
            <Icon className="w-5 h-5 text-primary" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
