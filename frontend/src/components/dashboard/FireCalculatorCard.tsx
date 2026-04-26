import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useFire } from '@/hooks/useFire';
import type { FireResult } from '@/types/fire.types';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import { Flame, Target, TrendingUp, Wallet, Clock, RotateCcw } from 'lucide-react';

function compact(value: number): string {
  if (value === 0) return '0';
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B`;
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (abs >= 1_000) return `${(value / 1_000).toFixed(0)}K`;
  return value.toFixed(0);
}

interface Overrides {
  monthlyExpense?: number;
  monthlyContribution?: number;
  netWorth?: number;
}

interface Baseline {
  monthlyExpense: number;
  monthlyContribution: number;
  netWorth: number;
}

export function FireCalculatorCard() {
  const { t } = useTranslation();
  const [withdrawalPct, setWithdrawalPct] = useState('4');
  const [returnPct, setReturnPct] = useState('7');
  const [overrides, setOverrides] = useState<Overrides>({});
  const baselineRef = useRef<Baseline | null>(null);
  const [, forceBaselineTick] = useState(0);

  const query = useMemo(() => {
    const w = Number(withdrawalPct.replace(',', '.')) / 100;
    const r = Number(returnPct.replace(',', '.')) / 100;
    return {
      withdrawalRate: Number.isFinite(w) && w > 0 ? w : undefined,
      expectedReturn: Number.isFinite(r) && r >= 0 ? r : undefined,
      monthlyContribution: overrides.monthlyContribution,
      monthlyExpense: overrides.monthlyExpense,
      netWorth: overrides.netWorth,
    };
  }, [withdrawalPct, returnPct, overrides]);

  const { data, isLoading } = useFire(query);

  useEffect(() => {
    if (!data) return;
    const hasOverride =
      overrides.monthlyExpense != null ||
      overrides.monthlyContribution != null ||
      overrides.netWorth != null;
    if (baselineRef.current || hasOverride) return;
    baselineRef.current = {
      monthlyExpense: data.avgMonthlyExpense,
      monthlyContribution: data.monthlyContribution,
      netWorth: data.currentNetWorth,
    };
    forceBaselineTick((v) => v + 1);
  }, [data, overrides]);

  const baseline = baselineRef.current;
  const hasOverride =
    overrides.monthlyExpense != null ||
    overrides.monthlyContribution != null ||
    overrides.netWorth != null;

  const chartData = useMemo(() => {
    if (!data) return [];
    return data.trajectory.map((p) => ({
      year: p.year,
      label: `+${p.year}y`,
      netWorth: p.netWorth,
    }));
  }, [data]);

  const progress = data ? Math.min(1, data.progressRatio) : 0;

  return (
    <Card className="overflow-hidden">
      <div className="px-5 py-4 border-b border-border/60 flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-lg bg-amber-500/10 flex items-center justify-center shrink-0">
            <Flame className="w-4 h-4 text-amber-400" />
          </div>
          <div className="space-y-0.5">
            <h3 className="text-sm font-medium">{t('fire.title')}</h3>
            <p className="text-[11px] text-muted-foreground">{t('fire.subtitle')}</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <RateInput
            label={t('fire.withdrawalRate')}
            value={withdrawalPct}
            onChange={setWithdrawalPct}
          />
          <RateInput
            label={t('fire.expectedReturn')}
            value={returnPct}
            onChange={setReturnPct}
          />
        </div>
      </div>

      <CardContent className="p-5 space-y-5">
        {isLoading && !data ? (
          <div className="h-40 flex items-center justify-center text-xs text-muted-foreground">
            {t('common.loading')}
          </div>
        ) : !data ? (
          <div className="h-40 flex items-center justify-center text-xs text-muted-foreground">
            {t('common.noData')}
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <HeroTile data={data} />
              <TimeTile data={data} />
            </div>

            {baseline && (
              <ScenarioPanel
                baseline={baseline}
                overrides={overrides}
                onChange={setOverrides}
                hasOverride={hasOverride}
              />
            )}

            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <MiniTile
                label={t('fire.netWorth')}
                value={formatTRY(data.currentNetWorth)}
                icon={<Wallet className="w-3.5 h-3.5 text-sky-400" />}
              />
              <MiniTile
                label={t('fire.monthlyContribution')}
                value={formatTRY(data.monthlyContribution)}
                icon={<TrendingUp className="w-3.5 h-3.5 text-emerald-400" />}
              />
              <MiniTile
                label={t('fire.savingsRate')}
                value={`${Math.round(data.savingsRate * 100)}%`}
                icon={<Flame className="w-3.5 h-3.5 text-amber-400" />}
              />
              <MiniTile
                label={t('fire.annualExpenses')}
                value={formatTRY(data.avgMonthlyExpense * 12)}
                icon={<Target className="w-3.5 h-3.5 text-rose-400" />}
              />
            </div>

            <div>
              <div className="flex items-center justify-between text-[11px] mb-1.5">
                <span className="text-muted-foreground">
                  {t('fire.progress')}
                </span>
                <span className="font-mono tabular-nums font-medium">
                  {Math.round(progress * 100)}%
                </span>
              </div>
              <div className="h-2 w-full rounded-full bg-border/50 overflow-hidden">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-emerald-400 via-amber-400 to-rose-400 transition-all duration-500"
                  style={{ width: `${progress * 100}%` }}
                />
              </div>
            </div>

            {chartData.length > 1 && (
              <div className="h-56">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                    <defs>
                      <linearGradient id="fireGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#fbbf24" stopOpacity={0.4} />
                        <stop offset="100%" stopColor="#fbbf24" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" vertical={false} />
                    <XAxis
                      dataKey="label"
                      tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                      stroke="hsl(var(--border))"
                    />
                    <YAxis
                      tickFormatter={(v: number) => compact(v)}
                      tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                      stroke="hsl(var(--border))"
                      width={48}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'hsl(var(--card))',
                        border: '1px solid hsl(var(--border))',
                        borderRadius: '6px',
                        fontSize: '12px',
                      }}
                      formatter={(v: number) => [formatTRY(v), t('fire.netWorth')]}
                    />
                    <ReferenceLine
                      y={data.targetNumber}
                      stroke="#f43f5e"
                      strokeDasharray="4 4"
                      strokeWidth={1.5}
                      label={{
                        value: `FI ${compact(data.targetNumber)}`,
                        fill: '#f43f5e',
                        fontSize: 10,
                        position: 'insideTopRight',
                      }}
                    />
                    <Area
                      type="monotone"
                      dataKey="netWorth"
                      stroke="#fbbf24"
                      strokeWidth={2}
                      fill="url(#fireGradient)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}

            {!data.sufficientData && (
              <p className="text-[11px] text-amber-400 text-center">
                {t('fire.needMoreData', { count: Math.max(0, 3 - data.samplesUsed) })}
              </p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function HeroTile({ data }: { data: FireResult }) {
  const { t } = useTranslation();
  return (
    <div className="relative rounded-xl border border-amber-400/30 bg-gradient-to-br from-amber-500/10 via-amber-500/5 to-transparent p-5 overflow-hidden">
      <Flame className="absolute -bottom-4 -right-4 w-24 h-24 text-amber-400/10" />
      <p className="text-[10px] uppercase tracking-widest text-amber-300/70 font-medium">
        {t('fire.targetNumber')}
      </p>
      <p className="text-3xl font-semibold font-mono tabular-nums tracking-tight mt-2">
        {formatTRY(data.targetNumber)}
      </p>
      <p className="text-[11px] text-muted-foreground mt-1">
        {t('fire.targetHint', {
          rate: `${Math.round(data.withdrawalRate * 10000) / 100}%`,
        })}
      </p>
    </div>
  );
}

function TimeTile({ data }: { data: FireResult }) {
  const { t } = useTranslation();
  const reached = data.progressRatio >= 1;
  const unreachable = !reached && data.monthsToFi == null;

  return (
    <div className="relative rounded-xl border border-border bg-card/50 p-5 overflow-hidden">
      <Clock className="absolute -bottom-4 -right-4 w-24 h-24 text-muted-foreground/5" />
      <p className="text-[10px] uppercase tracking-widest text-muted-foreground font-medium">
        {t('fire.timeToFi')}
      </p>

      {reached ? (
        <>
          <p className="text-3xl font-semibold font-mono tabular-nums tracking-tight mt-2 text-emerald-400">
            {t('fire.financiallyFree')}
          </p>
          <p className="text-[11px] text-muted-foreground mt-1">
            {t('fire.financiallyFreeHint')}
          </p>
        </>
      ) : unreachable ? (
        <>
          <p className="text-2xl font-semibold font-mono tabular-nums tracking-tight mt-2 text-rose-400">
            {t('fire.unreachable')}
          </p>
          <p className="text-[11px] text-muted-foreground mt-1">
            {t('fire.unreachableHint')}
          </p>
        </>
      ) : (
        <>
          <p className="text-3xl font-semibold font-mono tabular-nums tracking-tight mt-2">
            {data.yearsToFi?.toFixed(1)}
            <span className="text-base text-muted-foreground ml-1">
              {t('fire.years')}
            </span>
          </p>
          {data.projectedFiDate && (
            <p className="text-[11px] text-muted-foreground mt-1">
              {t('fire.projectedDate', {
                date: new Intl.DateTimeFormat(undefined, {
                  month: 'long',
                  year: 'numeric',
                }).format(new Date(data.projectedFiDate)),
              })}
            </p>
          )}
        </>
      )}
    </div>
  );
}

function MiniTile({
  label,
  value,
  icon,
}: {
  label: string;
  value: string;
  icon: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-border/60 bg-card/50 p-3">
      <div className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-muted-foreground mb-1.5">
        {icon}
        <span>{label}</span>
      </div>
      <p className="text-sm font-mono tabular-nums font-semibold">{value}</p>
    </div>
  );
}

interface ScenarioPanelProps {
  baseline: Baseline;
  overrides: Overrides;
  onChange: (next: Overrides) => void;
  hasOverride: boolean;
}

function ScenarioPanel({ baseline, overrides, onChange, hasOverride }: ScenarioPanelProps) {
  const { t } = useTranslation();

  const expense = overrides.monthlyExpense ?? baseline.monthlyExpense;
  const contribution = overrides.monthlyContribution ?? baseline.monthlyContribution;
  const netWorth = overrides.netWorth ?? baseline.netWorth;

  const expenseMax = Math.max(Math.ceil(baseline.monthlyExpense * 2), 1000);
  const contributionMax = Math.max(
    Math.ceil(Math.max(baseline.monthlyContribution, baseline.monthlyExpense) * 2),
    1000,
  );
  const netWorthMax = Math.max(Math.ceil(baseline.netWorth * 2), 10_000);

  return (
    <div className="rounded-xl border border-border/60 bg-card/40 p-4 space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-medium">{t('fire.scenario')}</p>
          <p className="text-[11px] text-muted-foreground mt-0.5">
            {t('fire.scenarioHint')}
          </p>
        </div>
        <button
          type="button"
          onClick={() => onChange({})}
          disabled={!hasOverride}
          className={cn(
            'inline-flex items-center gap-1 rounded-md border border-border/60 px-2 py-1 text-[11px] text-muted-foreground transition-colors',
            hasOverride
              ? 'hover:text-foreground hover:border-border'
              : 'opacity-50 cursor-not-allowed',
          )}
        >
          <RotateCcw className="w-3 h-3" />
          {t('fire.scenarioReset')}
        </button>
      </div>
      <div className="grid gap-3 md:grid-cols-3">
        <ScenarioSlider
          label={t('fire.scenarioExpense')}
          value={expense}
          min={0}
          max={expenseMax}
          step={100}
          baseline={baseline.monthlyExpense}
          onChange={(v) =>
            onChange({ ...overrides, monthlyExpense: v === baseline.monthlyExpense ? undefined : v })
          }
        />
        <ScenarioSlider
          label={t('fire.scenarioContribution')}
          value={contribution}
          min={0}
          max={contributionMax}
          step={100}
          baseline={baseline.monthlyContribution}
          onChange={(v) =>
            onChange({
              ...overrides,
              monthlyContribution: v === baseline.monthlyContribution ? undefined : v,
            })
          }
        />
        <ScenarioSlider
          label={t('fire.scenarioNetWorth')}
          value={netWorth}
          min={0}
          max={netWorthMax}
          step={1000}
          baseline={baseline.netWorth}
          onChange={(v) =>
            onChange({ ...overrides, netWorth: v === baseline.netWorth ? undefined : v })
          }
        />
      </div>
    </div>
  );
}

interface ScenarioSliderProps {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  baseline: number;
  onChange: (value: number) => void;
}

function ScenarioSlider({ label, value, min, max, step, baseline, onChange }: ScenarioSliderProps) {
  const { t } = useTranslation();
  const deviates = value !== baseline;
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</span>
        <span
          className={cn(
            'text-xs font-mono tabular-nums font-medium',
            deviates ? 'text-amber-400' : '',
          )}
        >
          {formatTRY(value)}
        </span>
      </div>
      <input
        type="range"
        className="scenario-slider"
        value={value}
        min={min}
        max={max}
        step={step}
        onChange={(e) => onChange(Number(e.target.value))}
      />
      <p className="text-[10px] text-muted-foreground/80 font-mono tabular-nums">
        {t('fire.scenarioBaseline', { value: formatTRY(baseline) })}
      </p>
    </div>
  );
}

function RateInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <label className="flex items-center gap-1.5">
      <span className="text-[10px] uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      <div className="relative">
        <Input
          type="number"
          step="0.1"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={cn('h-7 w-16 pr-6 text-xs font-mono tabular-nums text-right')}
        />
        <span className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground">
          %
        </span>
      </div>
    </label>
  );
}
