import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from 'recharts';
import type { PortfolioSnapshot } from '@/types/portfolio.types';
import { formatTRY, formatPercent } from '@/utils/formatters';
import { cn } from '@/lib/utils';

interface PortfolioHistoryChartProps {
  snapshots: PortfolioSnapshot[];
}

interface ChartPoint {
  date: string;
  iso: string;
  value: number;
  cost: number;
  pnl: number;
  pnlPercent: number | null;
  deltaFromStart: number;
}

/**
 * Portfolio value over time. Rendered as an area chart with a cyan-to-transparent
 * gradient fill, editorial-style header, and a custom tooltip that mirrors the
 * dark-theme card surface used elsewhere in the detail page.
 */
export function PortfolioHistoryChart({ snapshots }: PortfolioHistoryChartProps) {
  const { t, i18n } = useTranslation();

  const points = useMemo<ChartPoint[]>(() => {
    if (snapshots.length === 0) return [];
    const locale = i18n.resolvedLanguage === 'tr' ? 'tr-TR' : 'en-US';
    const formatter = new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'short' });
    const startValue = snapshots[0]?.totalValueTry ?? 0;

    return snapshots.map((s) => {
      const d = new Date(s.date);
      return {
        date: formatter.format(d),
        iso: s.date,
        value: s.totalValueTry ?? 0,
        cost: s.totalCostTry ?? 0,
        pnl: s.pnlTry ?? 0,
        pnlPercent: s.pnlPercent,
        deltaFromStart: (s.totalValueTry ?? 0) - startValue,
      };
    });
  }, [snapshots, i18n.resolvedLanguage]);

  const summary = useMemo(() => {
    if (points.length === 0) {
      return null;
    }
    const first = points[0];
    const last = points[points.length - 1];
    const change = last.value - first.value;
    const changePercent = first.value > 0 ? change / first.value : null;
    return {
      first,
      last,
      change,
      changePercent,
      positive: change >= 0,
      min: Math.min(...points.map((p) => p.value)),
      max: Math.max(...points.map((p) => p.value)),
    };
  }, [points]);

  if (points.length < 2 || !summary) {
    return (
      <div className="flex flex-col items-center justify-center py-10 text-center">
        <div className="w-10 h-10 rounded-full border border-dashed border-border flex items-center justify-center mb-3">
          <span className="text-muted-foreground text-sm font-mono">~</span>
        </div>
        <p className="text-sm text-muted-foreground max-w-[32ch]">
          {t('holdings.historyEmpty')}
        </p>
      </div>
    );
  }

  const accent = summary.positive ? 'hsl(172 70% 50%)' : 'hsl(0 75% 60%)';
  const gradientId = summary.positive ? 'historyGradientPos' : 'historyGradientNeg';

  return (
    <div className="space-y-5">
      {/* Summary strip */}
      <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto] gap-4 items-end">
        <div className="space-y-1">
          <div className="flex items-baseline gap-2">
            <span className="text-2xl font-semibold font-mono tabular-nums tracking-tight">
              {formatTRY(summary.last.value)}
            </span>
            <span
              className={cn(
                'text-xs font-mono tabular-nums font-medium px-1.5 py-0.5 rounded',
                summary.positive
                  ? 'text-positive bg-positive/10'
                  : 'text-negative bg-negative/10'
              )}
            >
              {summary.positive ? '+' : ''}
              {formatTRY(summary.change, true)}
              {summary.changePercent != null && (
                <span className="ml-1 opacity-80">
                  ({summary.positive ? '+' : ''}
                  {formatPercent(summary.changePercent)})
                </span>
              )}
            </span>
          </div>
          <p className="text-[11px] uppercase tracking-[0.14em] text-muted-foreground">
            {t('holdings.historyRange', {
              start: summary.first.date,
              end: summary.last.date,
            })}
          </p>
        </div>
        <div className="flex items-center gap-4 text-[11px] uppercase tracking-[0.14em] text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <span
              className="inline-block w-1.5 h-1.5 rounded-full"
              style={{ backgroundColor: accent }}
            />
            {t('holdings.historyLine')}
          </span>
          <span className="font-mono tabular-nums normal-case tracking-normal">
            {points.length} {t('holdings.historyPoints')}
          </span>
        </div>
      </div>

      {/* Chart */}
      <div className="h-[220px] w-full -ml-2">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={points} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={accent} stopOpacity={0.35} />
                <stop offset="70%" stopColor={accent} stopOpacity={0.05} />
                <stop offset="100%" stopColor={accent} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid
              stroke="hsl(var(--border))"
              strokeDasharray="2 4"
              vertical={false}
              opacity={0.4}
            />
            <XAxis
              dataKey="date"
              stroke="hsl(var(--muted-foreground))"
              fontSize={10}
              tickLine={false}
              axisLine={false}
              interval="preserveStartEnd"
              minTickGap={40}
              tick={{ fill: 'hsl(var(--muted-foreground))' }}
            />
            <YAxis
              stroke="hsl(var(--muted-foreground))"
              fontSize={10}
              tickLine={false}
              axisLine={false}
              width={64}
              tickFormatter={(v: number) => formatCompactTRY(v)}
              tick={{ fill: 'hsl(var(--muted-foreground))' }}
              domain={['auto', 'auto']}
            />
            <Tooltip
              cursor={{
                stroke: 'hsl(var(--border))',
                strokeWidth: 1,
                strokeDasharray: '2 4',
              }}
              content={<HistoryTooltip />}
            />
            <Area
              type="monotone"
              dataKey="value"
              stroke={accent}
              strokeWidth={2}
              fill={`url(#${gradientId})`}
              activeDot={{
                r: 4,
                stroke: accent,
                strokeWidth: 2,
                fill: 'hsl(var(--card))',
              }}
              isAnimationActive
              animationDuration={600}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

/** Dark-card tooltip that matches the surrounding detail page surfaces. */
function HistoryTooltip({ active, payload }: TooltipProps<number, string>) {
  const { t } = useTranslation();
  if (!active || !payload || payload.length === 0) return null;
  const point = payload[0].payload as ChartPoint;
  const positive = point.pnl >= 0;

  return (
    <div className="rounded-md border border-border bg-card/95 backdrop-blur px-3 py-2 shadow-lg shadow-black/20 min-w-[180px]">
      <p className="text-[10px] uppercase tracking-[0.14em] text-muted-foreground mb-1.5">
        {point.date}
      </p>
      <div className="flex items-baseline justify-between gap-3 mb-1">
        <span className="text-[11px] text-muted-foreground">{t('holdings.currentValue')}</span>
        <span className="text-sm font-mono tabular-nums font-semibold">
          {formatTRY(point.value)}
        </span>
      </div>
      <div className="flex items-baseline justify-between gap-3 mb-1">
        <span className="text-[11px] text-muted-foreground">{t('holdings.costBasis')}</span>
        <span className="text-xs font-mono tabular-nums text-muted-foreground">
          {formatTRY(point.cost)}
        </span>
      </div>
      <div className="mt-1.5 pt-1.5 border-t border-border/60 flex items-baseline justify-between gap-3">
        <span className="text-[11px] text-muted-foreground">{t('holdings.pnl')}</span>
        <span
          className={cn(
            'text-xs font-mono tabular-nums font-medium',
            positive ? 'text-positive' : 'text-negative'
          )}
        >
          {positive ? '+' : ''}
          {formatTRY(point.pnl, true)}
          {point.pnlPercent != null && (
            <span className="ml-1 opacity-70">
              ({positive ? '+' : ''}
              {formatPercent(point.pnlPercent)})
            </span>
          )}
        </span>
      </div>
    </div>
  );
}

/** Compact TRY formatter for Y axis ticks ("1.2M ₺", "45K ₺"). */
function formatCompactTRY(value: number): string {
  if (value === 0) return '0 ₺';
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B ₺`;
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M ₺`;
  if (abs >= 1_000) return `${(value / 1_000).toFixed(0)}K ₺`;
  return `${value.toFixed(0)} ₺`;
}
