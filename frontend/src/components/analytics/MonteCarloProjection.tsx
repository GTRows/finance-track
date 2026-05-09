import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  Legend,
} from 'recharts';
import { Activity, Loader2, Play, Plus, X } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/layout/EmptyState';
import { useFire } from '@/hooks/useFire';
import { useMonteCarloDefaults, useMonteCarloMutation } from '@/hooks/useAnalytics';
import type {
  AllocationClassDefault,
  AllocationClassInput,
  AssetClassLiteral,
  MonteCarloRequest,
  MonteCarloResponse,
} from '@/api/analytics.api';
import { formatCompactTRY, formatPercent, formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';

interface AllocationRow {
  assetClass: AssetClassLiteral;
  weight: number;
  annualMeanReturn: number;
  annualStdDev: number;
}

const ALL_CLASSES: AssetClassLiteral[] = [
  'STOCK',
  'BOND',
  'CASH',
  'CRYPTO',
  'GOLD',
  'FUND',
  'CURRENCY',
  'OTHER',
];

const ITERATIONS_MIN = 1000;
const ITERATIONS_MAX = 10000;
const ITERATIONS_STEP = 1000;
const HORIZON_MIN = 5;
const HORIZON_MAX = 50;

function defaultRow(seed: AllocationClassDefault): AllocationRow {
  return {
    assetClass: seed.assetClass,
    weight: seed.defaultWeight,
    annualMeanReturn: seed.annualMeanReturn,
    annualStdDev: seed.annualStdDev,
  };
}

export function MonteCarloProjection() {
  const { t } = useTranslation();
  const defaultsQuery = useMonteCarloDefaults();
  const fireQuery = useFire({});
  const mutation = useMonteCarloMutation();

  const [rows, setRows] = useState<AllocationRow[]>([]);
  const [iterations, setIterations] = useState<number>(ITERATIONS_MAX);
  const [horizonYears, setHorizonYears] = useState<number>(20);
  const [currentNetWorth, setCurrentNetWorth] = useState<number>(0);
  const [monthlyContribution, setMonthlyContribution] = useState<number>(0);
  const [targetNetWorth, setTargetNetWorth] = useState<number | null>(null);
  const [response, setResponse] = useState<MonteCarloResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Seed editor + slider state from server defaults the first time they resolve.
  useEffect(() => {
    if (!defaultsQuery.data || rows.length > 0) return;
    const visible = defaultsQuery.data.classes.filter((c) => c.defaultWeight > 0);
    setRows(visible.map(defaultRow));
    setIterations(defaultsQuery.data.defaultIterations);
    setHorizonYears(defaultsQuery.data.defaultHorizonYears);
  }, [defaultsQuery.data, rows.length]);

  // Pre-fill the three monetary inputs from the FIRE summary if it is available.
  useEffect(() => {
    if (!fireQuery.data) return;
    setCurrentNetWorth((prev) => (prev > 0 ? prev : Math.round(fireQuery.data.currentNetWorth)));
    setMonthlyContribution((prev) =>
      prev > 0 ? prev : Math.round(fireQuery.data.monthlyContribution),
    );
    setTargetNetWorth((prev) =>
      prev != null && prev > 0 ? prev : Math.round(fireQuery.data.targetNumber),
    );
  }, [fireQuery.data]);

  const totalWeight = useMemo(
    () => rows.reduce((acc, r) => acc + (Number.isFinite(r.weight) ? r.weight : 0), 0),
    [rows],
  );
  const weightSumValid = totalWeight >= 0.999 && totalWeight <= 1.001;

  const availableClasses = useMemo(
    () => ALL_CLASSES.filter((klass) => !rows.some((r) => r.assetClass === klass)),
    [rows],
  );

  const updateRow = (index: number, patch: Partial<AllocationRow>) => {
    setRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const removeRow = (index: number) => {
    setRows((prev) => prev.filter((_, i) => i !== index));
  };

  const addRow = (klass: AssetClassLiteral) => {
    const fallback = defaultsQuery.data?.classes.find((c) => c.assetClass === klass);
    setRows((prev) => [
      ...prev,
      {
        assetClass: klass,
        weight: 0,
        annualMeanReturn: fallback ? fallback.annualMeanReturn : 0,
        annualStdDev: fallback ? fallback.annualStdDev : 0.1,
      },
    ]);
  };

  const handleRun = () => {
    setErrorMessage(null);
    const request: MonteCarloRequest = {
      horizonYears,
      iterations,
      currentNetWorth,
      monthlyContribution,
      targetNetWorth: targetNetWorth ?? null,
      allocations: rows.map<AllocationClassInput>((row) => ({
        assetClass: row.assetClass,
        weight: row.weight,
        annualMeanReturn: row.annualMeanReturn,
        annualStdDev: row.annualStdDev,
      })),
    };
    mutation.mutate(request, {
      onSuccess: (data) => setResponse(data),
      onError: (err) => setErrorMessage(err.message ?? t('analytics.monteCarlo.errorTitle')),
    });
  };

  const isLoading = defaultsQuery.isLoading;

  return (
    <Card>
      <CardHeader className="pb-3 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <CardTitle className="text-sm font-medium">{t('analytics.monteCarlo.title')}</CardTitle>
          <CardDescription className="text-xs">
            {t('analytics.monteCarlo.description')}
          </CardDescription>
        </div>
        <button
          type="button"
          onClick={handleRun}
          disabled={!weightSumValid || mutation.isPending || rows.length === 0}
          className={cn(
            'inline-flex items-center gap-2 h-9 px-4 rounded-md text-sm font-medium tracking-tight transition-colors cursor-pointer',
            'bg-primary text-primary-foreground hover:bg-primary/90',
            'disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed',
          )}
        >
          {mutation.isPending ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          ) : (
            <Play className="w-3.5 h-3.5" />
          )}
          <span>
            {mutation.isPending
              ? t('analytics.monteCarlo.runningLabel')
              : t('analytics.monteCarlo.runButton')}
          </span>
        </button>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading ? (
          <div className="flex items-center justify-center h-[260px]">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <NumericField
                label={t('analytics.monteCarlo.currentNetWorthLabel')}
                value={currentNetWorth}
                onChange={(v) => setCurrentNetWorth(v ?? 0)}
              />
              <NumericField
                label={t('analytics.monteCarlo.monthlyContributionLabel')}
                value={monthlyContribution}
                onChange={(v) => setMonthlyContribution(v ?? 0)}
              />
              <NumericField
                label={`${t('analytics.monteCarlo.targetLabel')} (${t('analytics.monteCarlo.targetOptional')})`}
                value={targetNetWorth ?? null}
                onChange={(v) => setTargetNetWorth(v == null || v <= 0 ? null : v)}
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <SliderField
                label={t('analytics.monteCarlo.iterationsLabel', { value: iterations })}
                min={ITERATIONS_MIN}
                max={ITERATIONS_MAX}
                step={ITERATIONS_STEP}
                value={iterations}
                onChange={setIterations}
              />
              <SliderField
                label={t('analytics.monteCarlo.horizonLabel', { value: horizonYears })}
                min={HORIZON_MIN}
                max={HORIZON_MAX}
                step={1}
                value={horizonYears}
                onChange={setHorizonYears}
              />
            </div>

            <AllocationEditor
              rows={rows}
              onUpdate={updateRow}
              onRemove={removeRow}
              onAdd={addRow}
              availableClasses={availableClasses}
              totalWeight={totalWeight}
              weightSumValid={weightSumValid}
            />

            {errorMessage ? (
              <EmptyState
                icon={Activity}
                title={t('analytics.monteCarlo.errorTitle')}
                description={errorMessage}
              />
            ) : response ? (
              <FanChart response={response} />
            ) : (
              <EmptyState
                icon={Activity}
                title={t('analytics.monteCarlo.title')}
                description={t('analytics.monteCarlo.description')}
              />
            )}

            {response ? <SummaryCards response={response} /> : null}

            <p className="text-[11px] text-muted-foreground mt-3 leading-relaxed">
              {t('analytics.monteCarlo.defaultsFootnote')}
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}

interface AllocationEditorProps {
  rows: AllocationRow[];
  onUpdate: (index: number, patch: Partial<AllocationRow>) => void;
  onRemove: (index: number) => void;
  onAdd: (klass: AssetClassLiteral) => void;
  availableClasses: AssetClassLiteral[];
  totalWeight: number;
  weightSumValid: boolean;
}

function AllocationEditor({
  rows,
  onUpdate,
  onRemove,
  onAdd,
  availableClasses,
  totalWeight,
  weightSumValid,
}: AllocationEditorProps) {
  const { t } = useTranslation();
  return (
    <div className="rounded-md border border-border overflow-hidden">
      <table className="w-full text-xs">
        <thead className="bg-muted/40 text-muted-foreground">
          <tr>
            <th className="text-left font-medium px-3 py-2 w-[28%]">
              {t('analytics.monteCarlo.classHeader')}
            </th>
            <th className="text-right font-medium px-3 py-2">
              {t('analytics.monteCarlo.weightHeader')}
            </th>
            <th className="text-right font-medium px-3 py-2">
              {t('analytics.monteCarlo.meanHeader')}
            </th>
            <th className="text-right font-medium px-3 py-2">
              {t('analytics.monteCarlo.stddevHeader')}
            </th>
            <th className="px-3 py-2 w-[40px]" />
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={row.assetClass} className="border-t border-border">
              <td className="px-3 py-2 font-mono tabular-nums">{row.assetClass}</td>
              <td className="px-3 py-1 text-right">
                <PercentInput
                  value={row.weight}
                  onChange={(v) => onUpdate(index, { weight: v })}
                />
              </td>
              <td className="px-3 py-1 text-right">
                <PercentInput
                  value={row.annualMeanReturn}
                  onChange={(v) => onUpdate(index, { annualMeanReturn: v })}
                  allowNegative
                />
              </td>
              <td className="px-3 py-1 text-right">
                <PercentInput
                  value={row.annualStdDev}
                  onChange={(v) => onUpdate(index, { annualStdDev: v })}
                />
              </td>
              <td className="px-3 py-1 text-right">
                <button
                  type="button"
                  onClick={() => onRemove(index)}
                  aria-label={t('analytics.monteCarlo.removeRowAria')}
                  className="inline-flex items-center justify-center w-6 h-6 rounded text-muted-foreground hover:text-foreground hover:bg-accent/40 cursor-pointer"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="flex items-center justify-between gap-3 border-t border-border px-3 py-2 bg-muted/20">
        <details className="relative">
          <summary
            className={cn(
              'inline-flex items-center gap-1.5 text-xs font-medium cursor-pointer select-none',
              availableClasses.length === 0
                ? 'text-muted-foreground/60 pointer-events-none'
                : 'text-primary hover:text-primary/80',
            )}
          >
            <Plus className="w-3.5 h-3.5" />
            {t('analytics.monteCarlo.addClassLabel')}
          </summary>
          {availableClasses.length > 0 ? (
            <div className="absolute left-0 mt-2 z-30 w-[200px] rounded-md border border-border bg-card shadow-lg p-1">
              {availableClasses.map((klass) => (
                <button
                  key={klass}
                  type="button"
                  onClick={() => onAdd(klass)}
                  className="block w-full text-left text-xs px-2 py-1.5 rounded hover:bg-accent/40 cursor-pointer font-mono tabular-nums"
                >
                  {klass}
                </button>
              ))}
            </div>
          ) : null}
        </details>
        <div
          className={cn(
            'text-[11px] tabular-nums',
            weightSumValid ? 'text-muted-foreground' : 'text-negative',
          )}
        >
          {t('analytics.monteCarlo.weightSumWarning', {
            value: formatPercent(totalWeight, 1),
          })}
        </div>
      </div>
    </div>
  );
}

interface PercentInputProps {
  value: number;
  onChange: (value: number) => void;
  allowNegative?: boolean;
}

function PercentInput({ value, onChange, allowNegative = false }: PercentInputProps) {
  const display = (value * 100).toFixed(2);
  return (
    <input
      type="number"
      step="0.01"
      min={allowNegative ? undefined : 0}
      value={display}
      onChange={(e) => {
        const next = Number(e.target.value);
        if (Number.isNaN(next)) return;
        onChange(next / 100);
      }}
      className="w-20 h-7 px-2 rounded border border-border bg-background/60 text-right font-mono tabular-nums text-xs"
    />
  );
}

interface NumericFieldProps {
  label: string;
  value: number | null;
  onChange: (value: number | null) => void;
}

function NumericField({ label, value, onChange }: NumericFieldProps) {
  return (
    <label className="block space-y-1">
      <span className="text-xs text-muted-foreground font-medium">{label}</span>
      <input
        type="number"
        step="100"
        min="0"
        value={value ?? ''}
        onChange={(e) => {
          const v = e.target.value;
          if (v === '') {
            onChange(null);
            return;
          }
          const next = Number(v);
          onChange(Number.isNaN(next) ? null : next);
        }}
        className="w-full h-9 px-3 rounded-md border border-border bg-background/60 text-sm font-mono tabular-nums"
      />
    </label>
  );
}

interface SliderFieldProps {
  label: string;
  min: number;
  max: number;
  step: number;
  value: number;
  onChange: (value: number) => void;
}

function SliderField({ label, min, max, step, value, onChange }: SliderFieldProps) {
  return (
    <label className="block space-y-1">
      <span className="text-xs text-muted-foreground font-medium">{label}</span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-primary"
      />
    </label>
  );
}

interface FanChartProps {
  response: MonteCarloResponse;
}

function FanChart({ response }: FanChartProps) {
  const { t } = useTranslation();
  const data = useMemo(
    () =>
      response.fan.map((point) => ({
        year: point.year,
        p10: point.p10,
        p25: point.p25,
        p50: point.p50,
        p75: point.p75,
        p90: point.p90,
      })),
    [response.fan],
  );

  return (
    <div className="space-y-2">
      <p className="text-xs text-muted-foreground">{t('analytics.monteCarlo.chartHint')}</p>
      <div className="h-[280px] w-full -ml-2">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid
              stroke="hsl(var(--border))"
              strokeDasharray="2 4"
              vertical={false}
              opacity={0.4}
            />
            <XAxis
              dataKey="year"
              stroke="hsl(var(--muted-foreground))"
              fontSize={10}
              tickLine={false}
              axisLine={false}
              tickFormatter={(v: number) => `${v}y`}
            />
            <YAxis
              stroke="hsl(var(--muted-foreground))"
              fontSize={10}
              tickLine={false}
              axisLine={false}
              width={64}
              tickFormatter={formatCompactTRY}
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
              formatter={(v: number, key: string) => [formatTRY(v), key]}
            />
            <Legend
              verticalAlign="top"
              height={28}
              iconType="circle"
              iconSize={8}
              wrapperStyle={{ fontSize: '11px', color: 'hsl(var(--muted-foreground))' }}
            />
            <Area
              type="monotone"
              dataKey="p90"
              name={t('analytics.monteCarlo.chartLegendBand90')}
              stroke="none"
              fill="hsl(172 70% 50%)"
              fillOpacity={0.15}
            />
            <Area
              type="monotone"
              dataKey="p10"
              name=" "
              stroke="none"
              fill="hsl(var(--card))"
              fillOpacity={1}
            />
            <Area
              type="monotone"
              dataKey="p75"
              name={t('analytics.monteCarlo.chartLegendBand50')}
              stroke="none"
              fill="hsl(172 70% 50%)"
              fillOpacity={0.3}
            />
            <Area
              type="monotone"
              dataKey="p25"
              name=" "
              stroke="none"
              fill="hsl(var(--card))"
              fillOpacity={1}
            />
            <Line
              type="monotone"
              dataKey="p50"
              name={t('analytics.monteCarlo.chartLegendMedian')}
              stroke="hsl(172 70% 50%)"
              strokeWidth={2.5}
              dot={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

interface SummaryCardsProps {
  response: MonteCarloResponse;
}

function SummaryCards({ response }: SummaryCardsProps) {
  const { t } = useTranslation();
  const { summary, currentNetWorth, targetNetWorth } = response;

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <StatCard
        label={t('analytics.monteCarlo.summaryMedian')}
        value={formatTRY(summary.p50)}
      />
      <StatCard
        label={t('analytics.monteCarlo.summaryDownside')}
        value={formatTRY(summary.p10)}
        tone={summary.p10 < currentNetWorth ? 'negative' : undefined}
      />
      <StatCard
        label={t('analytics.monteCarlo.summaryUpside')}
        value={formatTRY(summary.p90)}
        tone="positive"
      />
      {targetNetWorth != null && summary.successProbability != null ? (
        <StatCard
          label={t('analytics.monteCarlo.summarySuccess')}
          value={formatPercent(summary.successProbability, 1)}
          tone={summary.successProbability >= 0.5 ? 'positive' : 'negative'}
        />
      ) : (
        <StatCard
          label={t('analytics.monteCarlo.summarySuccess')}
          value="—"
          hint={t('analytics.monteCarlo.summarySuccessEmpty')}
        />
      )}
    </div>
  );
}

interface StatCardProps {
  label: string;
  value: string;
  hint?: string;
  tone?: 'positive' | 'negative';
}

function StatCard({ label, value, hint, tone }: StatCardProps) {
  return (
    <div className="rounded-md border border-border bg-card px-4 py-3 space-y-1">
      <p className="text-[11px] text-muted-foreground font-medium uppercase tracking-wider">
        {label}
      </p>
      <p
        className={cn(
          'text-lg font-mono tabular-nums font-semibold tracking-tight truncate',
          tone === 'positive' && 'text-positive',
          tone === 'negative' && 'text-negative',
        )}
      >
        {value}
      </p>
      {hint ? <p className="text-[11px] text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
