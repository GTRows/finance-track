import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from 'recharts';
import { ChevronDown, GitCompareArrows, Loader2 } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/layout/EmptyState';
import { usePortfolios } from '@/hooks/usePortfolios';
import { usePortfolioComparison } from '@/hooks/useAnalytics';
import { formatCompactTRY, formatTRY, formatPercent, formatShortDate } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import type { Portfolio } from '@/types/portfolio.types';

/** 10-stop hue rotation derived from the existing benchmark palette. */
const PORTFOLIO_PALETTE: string[] = [
  'hsl(172 70% 50%)',
  'hsl(38 92% 55%)',
  'hsl(210 80% 62%)',
  'hsl(45 85% 52%)',
  'hsl(285 60% 60%)',
  'hsl(0 75% 60%)',
  'hsl(120 50% 50%)',
  'hsl(195 70% 55%)',
  'hsl(330 65% 60%)',
  'hsl(60 80% 55%)',
];

const MAX_PORTFOLIOS = 10;

type ValueMode = 'percent' | 'absolute';
type RangePreset = '1M' | '3M' | 'YTD' | '1Y' | 'ALL';

interface ComparePoint {
  date: string;
  dateLabel: string;
  /** Per-portfolio values keyed by portfolioId. */
  [seriesKey: string]: number | string;
}

function isoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function presetRange(preset: RangePreset): { from?: string; to?: string } {
  if (preset === 'ALL') return {};
  const now = new Date();
  const to = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (preset === 'YTD') {
    return { from: isoDate(new Date(now.getFullYear(), 0, 1)), to: isoDate(to) };
  }
  const monthsBack = preset === '1M' ? 1 : preset === '3M' ? 3 : 12;
  const from = new Date(now.getFullYear(), now.getMonth() - monthsBack, now.getDate());
  return { from: isoDate(from), to: isoDate(to) };
}

const RANGE_PRESETS: RangePreset[] = ['1M', '3M', 'YTD', '1Y', 'ALL'];

export function PortfolioComparisonChart() {
  const { t } = useTranslation();
  const portfoliosQuery = usePortfolios();
  const portfolios = useMemo<Portfolio[]>(() => portfoliosQuery.data ?? [], [portfoliosQuery.data]);

  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const seededRef = useRef(false);
  useEffect(() => {
    if (!seededRef.current && portfolios.length > 0) {
      seededRef.current = true;
      setSelectedIds(portfolios.slice(0, 2).map((p) => p.id));
    }
  }, [portfolios]);

  const [mode, setMode] = useState<ValueMode>('percent');
  const [preset, setPreset] = useState<RangePreset>('1Y');
  const range = useMemo(() => presetRange(preset), [preset]);

  const compareQuery = usePortfolioComparison(selectedIds, range.from, range.to);

  const portfolioMap = useMemo(() => {
    const m = new Map<string, Portfolio>();
    for (const p of portfolios) m.set(p.id, p);
    return m;
  }, [portfolios]);

  const colorByPortfolioId = useMemo(() => {
    const map = new Map<string, string>();
    portfolios.forEach((p, i) => map.set(p.id, PORTFOLIO_PALETTE[i % PORTFOLIO_PALETTE.length]));
    return map;
  }, [portfolios]);

  const chartData = useMemo<ComparePoint[]>(() => {
    if (!compareQuery.data) return [];
    const rowsByDate = new Map<string, ComparePoint>();
    const baseByPortfolio = new Map<string, number>();

    for (const series of compareQuery.data.series) {
      if (series.points.length === 0) continue;
      const baseValue = series.points[0].totalValueTry;
      baseByPortfolio.set(series.portfolioId, baseValue);
      for (const point of series.points) {
        const row = rowsByDate.get(point.date) ?? {
          date: point.date,
          dateLabel: formatShortDate(point.date),
        };
        const raw = point.totalValueTry;
        if (mode === 'percent') {
          row[series.portfolioId] = baseValue > 0 ? (raw / baseValue) * 100 : 100;
        } else {
          row[series.portfolioId] = raw;
        }
        row[`${series.portfolioId}__pnl`] = point.totalPnlTry;
        row[`${series.portfolioId}__abs`] = raw;
        rowsByDate.set(point.date, row);
      }
    }

    return Array.from(rowsByDate.values()).sort((a, b) => a.date.localeCompare(b.date));
  }, [compareQuery.data, mode]);

  const togglePortfolio = (id: string) => {
    setSelectedIds((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id);
      if (prev.length >= MAX_PORTFOLIOS) return prev;
      return [...prev, id];
    });
  };

  const yTickFormatter = (v: number) =>
    mode === 'percent' ? v.toFixed(0) : formatCompactTRY(v);

  const isLoading = portfoliosQuery.isLoading || compareQuery.isLoading;
  const isFetching = compareQuery.isFetching;

  return (
    <Card>
      <CardHeader className="pb-3 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <CardTitle className="text-sm font-medium">{t('analytics.compare.title')}</CardTitle>
          <CardDescription className="text-xs">
            {t('analytics.compare.description')}
          </CardDescription>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ModeToggle value={mode} onChange={setMode} />
          <RangePresetRow value={preset} onChange={setPreset} />
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex flex-wrap items-center gap-2 mb-4">
          <PortfolioMultiSelect
            portfolios={portfolios}
            selectedIds={selectedIds}
            onToggle={togglePortfolio}
            colorByPortfolioId={colorByPortfolioId}
          />
          {isFetching ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin text-muted-foreground" />
          ) : null}
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center h-[260px]">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        ) : selectedIds.length === 0 ? (
          <EmptyState
            icon={GitCompareArrows}
            title={t('analytics.compare.emptyTitle')}
            description={t('analytics.compare.emptyDesc')}
          />
        ) : compareQuery.isError ? (
          <EmptyState
            icon={GitCompareArrows}
            title={t('analytics.compare.loadingError')}
            description={t('analytics.compare.emptyDesc')}
          />
        ) : chartData.length === 0 ? (
          <EmptyState
            icon={GitCompareArrows}
            title={t('analytics.compare.emptyTitle')}
            description={t('analytics.compare.emptyDesc')}
          />
        ) : (
          <>
            <div className="h-[300px] w-full -ml-2">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart
                  data={chartData}
                  margin={{ top: 4, right: 8, left: 0, bottom: 0 }}
                >
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
                    width={mode === 'percent' ? 48 : 64}
                    domain={['auto', 'auto']}
                    tickFormatter={yTickFormatter}
                  />
                  <Tooltip
                    cursor={{
                      stroke: 'hsl(var(--border))',
                      strokeWidth: 1,
                      strokeDasharray: '2 4',
                    }}
                    content={
                      <CompareTooltip
                        mode={mode}
                        portfolioMap={portfolioMap}
                        colorByPortfolioId={colorByPortfolioId}
                      />
                    }
                  />
                  <Legend
                    verticalAlign="top"
                    height={28}
                    iconType="circle"
                    iconSize={8}
                    wrapperStyle={{
                      fontSize: '11px',
                      color: 'hsl(var(--muted-foreground))',
                    }}
                  />
                  {selectedIds.map((id) => (
                    <Line
                      key={id}
                      type="monotone"
                      dataKey={id}
                      name={portfolioMap.get(id)?.name ?? id}
                      stroke={colorByPortfolioId.get(id) ?? 'hsl(var(--muted-foreground))'}
                      strokeWidth={2}
                      dot={false}
                      connectNulls
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
            <p className="text-[11px] text-muted-foreground mt-3 leading-relaxed">
              {t('analytics.compare.realisedPnlNote')}
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}

interface ModeToggleProps {
  value: ValueMode;
  onChange: (mode: ValueMode) => void;
}

function ModeToggle({ value, onChange }: ModeToggleProps) {
  const { t } = useTranslation();
  const modes: Array<{ key: ValueMode; labelKey: string }> = [
    { key: 'percent', labelKey: 'analytics.compare.modePercent' },
    { key: 'absolute', labelKey: 'analytics.compare.modeAbsolute' },
  ];
  return (
    <div className="inline-flex items-center gap-0.5 rounded-md border border-border bg-background/40 p-0.5">
      {modes.map((m) => (
        <button
          key={m.key}
          type="button"
          onClick={() => onChange(m.key)}
          className={cn(
            'h-7 px-2.5 rounded text-[11px] font-medium tracking-tight transition-colors cursor-pointer',
            value === m.key
              ? 'bg-primary/15 text-primary'
              : 'text-muted-foreground hover:text-foreground'
          )}
        >
          {t(m.labelKey)}
        </button>
      ))}
    </div>
  );
}

interface RangePresetRowProps {
  value: RangePreset;
  onChange: (preset: RangePreset) => void;
}

function RangePresetRow({ value, onChange }: RangePresetRowProps) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-1">
      {RANGE_PRESETS.map((p) => (
        <button
          key={p}
          type="button"
          onClick={() => onChange(p)}
          className={cn(
            'h-7 px-2.5 rounded-md border text-[11px] font-medium tabular-nums transition-colors cursor-pointer',
            value === p
              ? 'bg-primary/10 border-primary/40 text-primary'
              : 'border-border text-muted-foreground hover:text-foreground hover:border-border/80'
          )}
        >
          {t(`analytics.compare.range${p}`)}
        </button>
      ))}
    </div>
  );
}

interface PortfolioMultiSelectProps {
  portfolios: Portfolio[];
  selectedIds: string[];
  onToggle: (id: string) => void;
  colorByPortfolioId: Map<string, string>;
}

function PortfolioMultiSelect({
  portfolios,
  selectedIds,
  onToggle,
  colorByPortfolioId,
}: PortfolioMultiSelectProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const selectionLabel =
    selectedIds.length === 0
      ? t('analytics.compare.selectPortfoliosPlaceholder')
      : t('analytics.compare.selectPortfolios', { count: selectedIds.length });

  const atCap = selectedIds.length >= MAX_PORTFOLIOS;

  return (
    <div ref={rootRef} className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="inline-flex items-center gap-2 h-9 px-3 rounded-md border border-border bg-background/40 text-sm text-foreground hover:border-primary/50 hover:bg-accent/40 transition-colors cursor-pointer"
      >
        <span className="tracking-tight">{selectionLabel}</span>
        <ChevronDown className="w-3.5 h-3.5 text-muted-foreground" />
      </button>
      {open ? (
        <div className="absolute left-0 mt-2 z-30 w-[280px] max-h-[320px] overflow-y-auto rounded-lg border border-border bg-card shadow-xl shadow-black/30 p-2">
          {portfolios.length === 0 ? (
            <p className="text-xs text-muted-foreground px-2 py-3 text-center">
              {t('analytics.compare.noPortfoliosSelected')}
            </p>
          ) : (
            <ul className="space-y-0.5">
              {portfolios.map((p) => {
                const checked = selectedIds.includes(p.id);
                const disabled = !checked && atCap;
                return (
                  <li key={p.id}>
                    <label
                      className={cn(
                        'flex items-center gap-2 h-8 px-2 rounded-md text-xs cursor-pointer transition-colors',
                        disabled
                          ? 'opacity-50 cursor-not-allowed'
                          : 'hover:bg-accent/40'
                      )}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={disabled}
                        onChange={() => onToggle(p.id)}
                        className="w-3.5 h-3.5 rounded border-border accent-primary"
                      />
                      <span
                        className="inline-block w-1.5 h-1.5 rounded-full"
                        style={{
                          backgroundColor:
                            colorByPortfolioId.get(p.id) ?? 'hsl(var(--muted-foreground))',
                        }}
                      />
                      <span className="truncate">{p.name}</span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  );
}

interface CompareTooltipProps extends TooltipProps<number, string> {
  mode: ValueMode;
  portfolioMap: Map<string, Portfolio>;
  colorByPortfolioId: Map<string, string>;
}

function CompareTooltip({
  active,
  payload,
  mode,
  portfolioMap,
  colorByPortfolioId,
}: CompareTooltipProps) {
  const { t } = useTranslation();
  if (!active || !payload || payload.length === 0) return null;
  const first = payload[0]?.payload as ComparePoint | undefined;
  if (!first) return null;

  return (
    <div className="rounded-md border border-border bg-card/95 backdrop-blur px-3 py-2 shadow-lg shadow-black/20 min-w-[220px]">
      <p className="text-[10px] uppercase tracking-[0.14em] text-muted-foreground mb-1.5">
        {first.dateLabel}
      </p>
      {payload.map((entry) => {
        const portfolioId = String(entry.dataKey ?? '');
        const portfolio = portfolioMap.get(portfolioId);
        const color = colorByPortfolioId.get(portfolioId) ?? 'hsl(var(--muted-foreground))';
        const value = typeof entry.value === 'number' ? entry.value : 0;
        const absoluteRaw = first[`${portfolioId}__abs`];
        const pnlRaw = first[`${portfolioId}__pnl`];
        const absolute = typeof absoluteRaw === 'number' ? absoluteRaw : null;
        const pnl = typeof pnlRaw === 'number' ? pnlRaw : null;
        const valueLabel =
          mode === 'percent'
            ? `${value.toFixed(1)}`
            : formatTRY(value);
        return (
          <div
            key={portfolioId}
            className="flex items-baseline justify-between gap-3 mb-1 last:mb-0"
          >
            <span className="flex items-center gap-1.5 min-w-0">
              <span
                className="inline-block w-1.5 h-1.5 rounded-full flex-shrink-0"
                style={{ backgroundColor: color }}
              />
              <span className="text-xs text-muted-foreground truncate">
                {portfolio?.name ?? portfolioId}
              </span>
            </span>
            <span className="text-xs font-mono tabular-nums font-semibold flex-shrink-0">
              {valueLabel}
              {mode === 'percent' && absolute != null ? (
                <span className="ml-1 opacity-70 font-normal">
                  ({formatTRY(absolute)})
                </span>
              ) : null}
              {pnl != null ? (
                <span
                  className={cn(
                    'ml-1.5 opacity-80',
                    pnl >= 0 ? 'text-positive' : 'text-negative'
                  )}
                >
                  {pnl >= 0 ? '+' : ''}
                  {formatPercent(absolute && absolute !== 0 ? pnl / absolute : 0)}
                </span>
              ) : null}
              <span className="sr-only">{t('analytics.compare.tooltipValue')}</span>
            </span>
          </div>
        );
      })}
    </div>
  );
}
