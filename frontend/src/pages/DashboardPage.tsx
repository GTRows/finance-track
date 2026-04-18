import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useAuthStore } from '@/store/auth.store';
import { useDashboard } from '@/hooks/useDashboard';
import { useMonthlySummaries } from '@/hooks/useBudget';
import { usePortfolios } from '@/hooks/usePortfolios';
import { usePortfolioSnapshotsAggregate } from '@/hooks/useAnalytics';
import { LivePriceTicker } from '@/components/dashboard/LivePriceTicker';
import { NetWorthHistoryCard } from '@/components/dashboard/NetWorthHistoryCard';
import { SavingsGoalsCard } from '@/components/dashboard/SavingsGoalsCard';
import { DebtTrackerCard } from '@/components/dashboard/DebtTrackerCard';
import { FireCalculatorCard } from '@/components/dashboard/FireCalculatorCard';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatTRY, formatPercent, formatMonth, formatShortDate } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import { getPortfolioTypeMeta } from '@/components/portfolio/portfolio-types';
import type { PortfolioType } from '@/types/portfolio.types';
import {
  TrendingUp,
  TrendingDown,
  Wallet,
  PiggyBank,
  ArrowUpRight,
  ArrowDownRight,
  Briefcase,
  Receipt,
} from 'lucide-react';

function compactTRY(value: number): string {
  if (value === 0) return '0';
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B`;
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (abs >= 1_000) return `${(value / 1_000).toFixed(0)}K`;
  return `${value.toFixed(0)}`;
}

export function DashboardPage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const { data } = useDashboard();
  const portfoliosQuery = usePortfolios();
  const snapshots = usePortfolioSnapshotsAggregate(portfoliosQuery.data);
  const summariesQuery = useMonthlySummaries();

  const portfolioSeries = useMemo(() => {
    return snapshots.data.map((p) => ({
      ...p,
      dateLabel: formatShortDate(p.date),
    }));
  }, [snapshots.data]);

  const budgetSeries = useMemo(() => {
    return (summariesQuery.data ?? [])
      .slice()
      .sort((a, b) => a.period.localeCompare(b.period))
      .slice(-6)
      .map((s) => ({
        period: s.period,
        periodLabel: formatMonth(s.period),
        income: s.totalIncome,
        expense: s.totalExpense,
      }));
  }, [summariesQuery.data]);

  const hasPortfolioHistory = portfolioSeries.length >= 2;
  const hasBudgetHistory = budgetSeries.length >= 1;

  const greeting = getGreeting(t);

  return (
    <div className="space-y-6 max-w-[1200px]">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          {greeting}, {user?.username}
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          {t('dashboard.overviewSubtitle')}
        </p>
      </div>

      <LivePriceTicker />

      {/* KPI Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          title={t('dashboard.netWorth')}
          value={data ? formatTRY(data.totalNetWorth) : '--'}
          icon={<Wallet className="w-5 h-5 text-primary" />}
          iconBg="bg-primary/10"
          subtitle={data ? `${data.portfolios.length} ${t('dashboard.portfolioCount')}` : undefined}
        />
        <KpiCard
          title={t('dashboard.monthlyIncome')}
          value={data ? formatTRY(data.budget.income) : '--'}
          icon={<TrendingUp className="w-5 h-5 text-emerald-400" />}
          iconBg="bg-emerald-500/10"
          valueClass="text-emerald-400"
          subtitle={data?.budget.period}
        />
        <KpiCard
          title={t('dashboard.monthlyExpenses')}
          value={data ? formatTRY(data.budget.expense) : '--'}
          icon={<TrendingDown className="w-5 h-5 text-red-400" />}
          iconBg="bg-red-500/10"
          valueClass="text-red-400"
          subtitle={data?.budget.period}
        />
        <KpiCard
          title={t('dashboard.savingsRate')}
          value={data ? `%${data.budget.savingsRate.toFixed(1)}` : '--'}
          icon={<PiggyBank className="w-5 h-5 text-primary" />}
          iconBg="bg-primary/10"
          subtitle={data && data.budget.net > 0
            ? `+${formatTRY(data.budget.net)} ${t('budget.netHint')}`
            : undefined}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">
        <Card className="lg:col-span-3">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">{t('dashboard.netWorthTrend')}</CardTitle>
          </CardHeader>
          <CardContent>
            {!hasPortfolioHistory ? (
              <EmptyBlock icon={TrendingUp} text={t('dashboard.netWorthTrendEmpty')} />
            ) : (
              <div className="h-[220px] w-full -ml-2">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={portfolioSeries} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                    <defs>
                      <linearGradient id="dashNetWorth" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="hsl(172 70% 50%)" stopOpacity={0.35} />
                        <stop offset="100%" stopColor="hsl(172 70% 50%)" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="2 4" vertical={false} opacity={0.4} />
                    <XAxis dataKey="dateLabel" stroke="hsl(var(--muted-foreground))" fontSize={10} tickLine={false} axisLine={false} minTickGap={40} />
                    <YAxis stroke="hsl(var(--muted-foreground))" fontSize={10} tickLine={false} axisLine={false} width={56} tickFormatter={compactTRY} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'hsl(var(--card))',
                        border: '1px solid hsl(var(--border))',
                        borderRadius: '6px',
                        fontSize: '12px',
                      }}
                      formatter={(v: number) => [formatTRY(v), t('analytics.value')]}
                    />
                    <Area
                      type="monotone"
                      dataKey="totalValueTry"
                      stroke="hsl(172 70% 50%)"
                      strokeWidth={2}
                      fill="url(#dashNetWorth)"
                      isAnimationActive
                      animationDuration={500}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">{t('dashboard.incomeExpenseTrend')}</CardTitle>
          </CardHeader>
          <CardContent>
            {!hasBudgetHistory ? (
              <EmptyBlock icon={PiggyBank} text={t('dashboard.incomeExpenseTrendEmpty')} />
            ) : (
              <div className="h-[220px] w-full -ml-2">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={budgetSeries} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                    <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="2 4" vertical={false} opacity={0.4} />
                    <XAxis dataKey="periodLabel" stroke="hsl(var(--muted-foreground))" fontSize={10} tickLine={false} axisLine={false} minTickGap={24} />
                    <YAxis stroke="hsl(var(--muted-foreground))" fontSize={10} tickLine={false} axisLine={false} width={56} tickFormatter={compactTRY} />
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
                      height={24}
                      iconType="circle"
                      iconSize={7}
                      wrapperStyle={{ fontSize: '11px', color: 'hsl(var(--muted-foreground))' }}
                    />
                    <Bar dataKey="income" name={t('analytics.income')} fill="hsl(172 70% 50%)" radius={[3, 3, 0, 0]} />
                    <Bar dataKey="expense" name={t('analytics.expense')} fill="hsl(0 75% 60%)" radius={[3, 3, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <NetWorthHistoryCard />

      <SavingsGoalsCard />

      <DebtTrackerCard />

      <FireCalculatorCard />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Portfolios */}
        <Card className="lg:col-span-2">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">{t('dashboard.portfolioPerformance')}</CardTitle>
          </CardHeader>
          <CardContent>
            {!data || data.portfolios.length === 0 ? (
              <EmptyBlock
                icon={Briefcase}
                text={t('dashboard.portfolioPerformanceEmpty')}
              />
            ) : (
              <div className="space-y-3">
                {data.portfolios.map((p) => {
                  const meta = getPortfolioTypeMeta(p.portfolioType as PortfolioType);
                  const positive = p.pnlTry >= 0;
                  return (
                    <Link
                      key={p.id}
                      to={`/portfolio/${p.id}`}
                      className="flex items-center gap-4 p-3 rounded-lg border border-border/50 hover:bg-accent/30 transition-colors group"
                    >
                      <div
                        className={cn('w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0', meta.badgeClass)}
                      >
                        <meta.icon className="w-4 h-4" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium truncate">{p.name}</p>
                        <p className="text-[11px] text-muted-foreground">{t(meta.labelKey)}</p>
                      </div>
                      <div className="text-right">
                        <p className="text-sm font-mono tabular-nums font-medium">
                          {formatTRY(p.valueTry)}
                        </p>
                        <div className="flex items-center justify-end gap-1">
                          {positive ? (
                            <ArrowUpRight className="w-3 h-3 text-emerald-400" />
                          ) : (
                            <ArrowDownRight className="w-3 h-3 text-red-400" />
                          )}
                          <span
                            className={cn(
                              'text-[11px] font-mono tabular-nums',
                              positive ? 'text-emerald-400' : 'text-red-400'
                            )}
                          >
                            {positive ? '+' : ''}{formatTRY(p.pnlTry)}
                            {p.pnlPercent != null && (
                              <span className="ml-1 opacity-70">
                                ({positive ? '+' : ''}{formatPercent(p.pnlPercent)})
                              </span>
                            )}
                          </span>
                        </div>
                      </div>
                    </Link>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Upcoming bills */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">{t('dashboard.upcomingBills')}</CardTitle>
          </CardHeader>
          <CardContent>
            {!data || data.upcomingBills.length === 0 ? (
              <EmptyBlock icon={Receipt} text={t('dashboard.upcomingBillsDesc')} />
            ) : (
              <div className="space-y-2">
                {data.upcomingBills.map((bill) => {
                  const urgent = bill.daysUntilDue <= 3;
                  return (
                    <div
                      key={bill.id}
                      className="flex items-center gap-3 p-2.5 rounded-lg border border-border/50"
                    >
                      <div
                        className={cn(
                          'w-2 h-2 rounded-full flex-shrink-0',
                          urgent ? 'bg-red-400 animate-pulse' : 'bg-amber-400'
                        )}
                      />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm truncate">{bill.name}</p>
                        <p className={cn(
                          'text-[11px]',
                          urgent ? 'text-red-400 font-medium' : 'text-muted-foreground'
                        )}>
                          {bill.daysUntilDue === 0
                            ? t('bills.dueToday')
                            : t('bills.daysLeft', { count: bill.daysUntilDue })}
                        </p>
                      </div>
                      <span className="text-sm font-mono tabular-nums font-medium">
                        {formatTRY(bill.amount)}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function KpiCard({
  title,
  value,
  icon,
  iconBg,
  valueClass,
  subtitle,
}: {
  title: string;
  value: string;
  icon: React.ReactNode;
  iconBg: string;
  valueClass?: string;
  subtitle?: string;
}) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between">
          <div className="space-y-2">
            <p className="text-sm text-muted-foreground font-medium">{title}</p>
            <p className={cn('text-2xl font-semibold font-mono tabular-nums tracking-tight', valueClass)}>
              {value}
            </p>
            {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
          </div>
          <div className={cn('w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0', iconBg)}>
            {icon}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function EmptyBlock({ icon: Icon, text }: { icon: React.ElementType; text: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <div className="w-10 h-10 rounded-xl bg-muted flex items-center justify-center mb-3">
        <Icon className="w-5 h-5 text-muted-foreground" />
      </div>
      <p className="text-sm text-muted-foreground max-w-[240px]">{text}</p>
    </div>
  );
}

function getGreeting(t: (key: string) => string): string {
  const hour = new Date().getHours();
  if (hour < 12) return t('dashboard.greetingMorning');
  if (hour < 18) return t('dashboard.greetingAfternoon');
  return t('dashboard.greetingEvening');
}
