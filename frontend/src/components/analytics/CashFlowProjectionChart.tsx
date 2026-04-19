import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Loader2, TrendingUp } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/layout/EmptyState';
import { useCashFlowProjection } from '@/hooks/useAnalytics';
import { formatMonth, formatTRY } from '@/utils/formatters';

function compactTRY(value: number): string {
  if (value === 0) return '0 ₺';
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B ₺`;
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M ₺`;
  if (abs >= 1_000) return `${(value / 1_000).toFixed(0)}K ₺`;
  return `${value.toFixed(0)} ₺`;
}

export function CashFlowProjectionChart() {
  const { t } = useTranslation();
  const { data, isLoading } = useCashFlowProjection(12);

  const chartData = useMemo(() => {
    if (!data) return [];
    return data.months.map((m) => ({
      period: m.period,
      label: formatMonth(m.period),
      income: m.projectedIncome,
      expense: -m.projectedExpense,
      balance: m.endingBalance,
    }));
  }, [data]);

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-medium">{t('analytics.projectionTitle')}</CardTitle>
        <CardDescription className="text-xs">{t('analytics.projectionHint')}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex items-center justify-center h-[260px]">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        ) : !data || chartData.length === 0 ? (
          <EmptyState
            icon={TrendingUp}
            title={t('analytics.emptyBudgetTitle')}
            description={t('analytics.emptyBudgetDesc')}
          />
        ) : (
          <div className="space-y-4">
            <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
              <MiniStat
                label={t('analytics.projectionAvgIncome')}
                value={formatTRY(data.avgMonthlyIncome)}
                tone="positive"
              />
              <MiniStat
                label={t('analytics.projectionAvgExpense')}
                value={formatTRY(data.avgMonthlyExpense)}
                tone="negative"
              />
              <MiniStat
                label={t('analytics.projectionAvgNet')}
                value={formatTRY(data.avgMonthlyNet)}
                tone={data.avgMonthlyNet >= 0 ? 'positive' : 'negative'}
              />
            </div>

            {!data.sufficient && (
              <p className="text-xs text-muted-foreground italic">
                {t('analytics.projectionInsufficient')}
              </p>
            )}

            <div className="h-[280px] w-full -ml-2">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={chartData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid
                    stroke="hsl(var(--border))"
                    strokeDasharray="2 4"
                    vertical={false}
                    opacity={0.4}
                  />
                  <XAxis
                    dataKey="label"
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    minTickGap={24}
                  />
                  <YAxis
                    yAxisId="flow"
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    width={64}
                    tickFormatter={compactTRY}
                  />
                  <YAxis
                    yAxisId="balance"
                    orientation="right"
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    width={64}
                    tickFormatter={compactTRY}
                  />
                  <Tooltip
                    cursor={{ fill: 'hsl(var(--accent))', opacity: 0.25 }}
                    contentStyle={{
                      backgroundColor: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: '6px',
                      fontSize: '12px',
                    }}
                    formatter={(v: number, key: string) => {
                      const abs = Math.abs(v);
                      const name =
                        key === 'income'
                          ? t('analytics.income')
                          : key === 'expense'
                            ? t('analytics.expense')
                            : t('analytics.projectionEndingBalance');
                      return [formatTRY(abs), name];
                    }}
                  />
                  <Legend
                    verticalAlign="top"
                    height={28}
                    iconType="circle"
                    iconSize={8}
                    wrapperStyle={{ fontSize: '11px', color: 'hsl(var(--muted-foreground))' }}
                  />
                  <Bar
                    yAxisId="flow"
                    dataKey="income"
                    name={t('analytics.income')}
                    fill="hsl(172 70% 50%)"
                    radius={[3, 3, 0, 0]}
                  />
                  <Bar
                    yAxisId="flow"
                    dataKey="expense"
                    name={t('analytics.expense')}
                    fill="hsl(0 75% 60%)"
                    radius={[0, 0, 3, 3]}
                  />
                  <Line
                    yAxisId="balance"
                    type="monotone"
                    dataKey="balance"
                    name={t('analytics.projectionEndingBalance')}
                    stroke="hsl(48 96% 60%)"
                    strokeWidth={2}
                    dot={{ r: 2.5, fill: 'hsl(48 96% 60%)', strokeWidth: 0 }}
                  />
                </ComposedChart>
              </ResponsiveContainer>
            </div>

            <p className="text-xs text-muted-foreground">
              {t('analytics.projectionSamples', { count: data.sampleMonths })}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

interface MiniStatProps {
  label: string;
  value: string;
  tone?: 'positive' | 'negative';
}

function MiniStat({ label, value, tone }: MiniStatProps) {
  const toneClass =
    tone === 'positive' ? 'text-positive' : tone === 'negative' ? 'text-negative' : '';
  return (
    <div className="rounded-md border border-border/60 bg-card/50 px-4 py-3">
      <p className="text-[11px] uppercase tracking-wide text-muted-foreground font-medium">
        {label}
      </p>
      <p className={`mt-1 text-base font-semibold font-mono tabular-nums ${toneClass}`}>{value}</p>
    </div>
  );
}
