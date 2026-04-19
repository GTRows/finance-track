import { useMemo, useState } from 'react';
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
} from 'recharts';
import { Compass, Loader2 } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/layout/EmptyState';
import { useBenchmarks } from '@/hooks/useAnalytics';
import type { BenchmarkSeries } from '@/api/analytics.api';
import type { AggregatedSnapshotPoint } from '@/hooks/useAnalytics';
import { formatShortDate } from '@/utils/formatters';
import { cn } from '@/lib/utils';

interface BenchmarkOverlayChartProps {
  snapshots: AggregatedSnapshotPoint[];
}

const BENCHMARK_COLORS: Record<string, string> = {
  PORTFOLIO: 'hsl(172 70% 50%)',
  BIST100: 'hsl(38 92% 55%)',
  SP500: 'hsl(210 80% 62%)',
  GOLD: 'hsl(45 85% 52%)',
};

const RANGE_OPTIONS: Array<{ value: number; labelKey: string }> = [
  { value: 90, labelKey: 'benchmarks.range90' },
  { value: 180, labelKey: 'benchmarks.range180' },
  { value: 365, labelKey: 'benchmarks.range365' },
];

export function BenchmarkOverlayChart({ snapshots }: BenchmarkOverlayChartProps) {
  const { t } = useTranslation();
  const [days, setDays] = useState(365);
  const benchmarks = useBenchmarks(days);

  const chartData = useMemo(() => {
    if (snapshots.length < 2 || !benchmarks.data) return [];
    const cutoffMs = Date.now() - days * 24 * 60 * 60 * 1000;
    const windowSnapshots = snapshots.filter((s) => new Date(s.date).getTime() >= cutoffMs);
    if (windowSnapshots.length < 2) return [];

    const portfolioBase = windowSnapshots[0].totalValueTry;
    if (portfolioBase <= 0) return [];

    const seriesBaseMap = new Map<string, number>();
    const seriesMap = new Map<string, Map<string, number>>();
    for (const series of benchmarks.data.series) {
      const inRange = series.points.filter((p) => new Date(p.date).getTime() >= cutoffMs);
      if (inRange.length === 0) continue;
      const base = inRange[0].close;
      if (base <= 0) continue;
      seriesBaseMap.set(series.code, base);
      const byDate = new Map<string, number>();
      for (const p of inRange) byDate.set(p.date, p.close);
      seriesMap.set(series.code, byDate);
    }

    return windowSnapshots.map((snap) => {
      const row: Record<string, number | string> = {
        date: snap.date,
        dateLabel: formatShortDate(snap.date),
        PORTFOLIO: (snap.totalValueTry / portfolioBase) * 100,
      };
      for (const [code, byDate] of seriesMap.entries()) {
        const base = seriesBaseMap.get(code) ?? 0;
        const close = nearestClose(byDate, snap.date);
        if (close != null && base > 0) {
          row[code] = (close / base) * 100;
        }
      }
      return row;
    });
  }, [snapshots, benchmarks.data, days]);

  const activeBenchmarks = useMemo(() => {
    if (!benchmarks.data) return [] as BenchmarkSeries[];
    return benchmarks.data.series.filter((s) => s.points.length > 0);
  }, [benchmarks.data]);

  return (
    <Card>
      <CardHeader className="pb-3 flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
        <div>
          <CardTitle className="text-sm font-medium">{t('benchmarks.title')}</CardTitle>
          <CardDescription className="text-xs">{t('benchmarks.hint')}</CardDescription>
        </div>
        <div className="flex items-center gap-1">
          {RANGE_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              onClick={() => setDays(opt.value)}
              className={cn(
                'h-7 px-2.5 rounded-md border text-[11px] font-medium tabular-nums transition-colors cursor-pointer',
                days === opt.value
                  ? 'bg-primary/10 border-primary/40 text-primary'
                  : 'border-border text-muted-foreground hover:text-foreground hover:border-border/80'
              )}
            >
              {t(opt.labelKey)}
            </button>
          ))}
        </div>
      </CardHeader>
      <CardContent>
        {benchmarks.isLoading ? (
          <div className="flex items-center justify-center h-[260px]">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        ) : snapshots.length < 2 ? (
          <EmptyState
            icon={Compass}
            title={t('benchmarks.emptyTitle')}
            description={t('benchmarks.emptyDesc')}
          />
        ) : chartData.length < 2 || activeBenchmarks.length === 0 ? (
          <EmptyState
            icon={Compass}
            title={t('benchmarks.unavailableTitle')}
            description={t('benchmarks.unavailableDesc')}
          />
        ) : (
          <>
            <div className="h-[280px] w-full -ml-2">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
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
                    width={48}
                    domain={['auto', 'auto']}
                    tickFormatter={(v: number) => v.toFixed(0)}
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
                    formatter={(v: number, name: string) => [
                      `${v.toFixed(1)}`,
                      t(`benchmarks.labels.${name}` as const, { defaultValue: name }),
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
                    dataKey="PORTFOLIO"
                    name={t('benchmarks.labels.PORTFOLIO')}
                    stroke={BENCHMARK_COLORS.PORTFOLIO}
                    strokeWidth={2.5}
                    dot={false}
                  />
                  {activeBenchmarks.map((series) => (
                    <Line
                      key={series.code}
                      type="monotone"
                      dataKey={series.code}
                      name={t(`benchmarks.labels.${series.code}` as const, {
                        defaultValue: series.code,
                      })}
                      stroke={BENCHMARK_COLORS[series.code] ?? 'hsl(var(--muted-foreground))'}
                      strokeWidth={1.5}
                      strokeDasharray="4 3"
                      dot={false}
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
            <p className="text-[11px] text-muted-foreground mt-3 leading-relaxed">
              {t('benchmarks.footnote')}
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function nearestClose(byDate: Map<string, number>, date: string): number | null {
  const direct = byDate.get(date);
  if (direct != null) return direct;
  const target = new Date(date).getTime();
  let bestDiff = Number.POSITIVE_INFINITY;
  let best: number | null = null;
  for (const [candidate, value] of byDate.entries()) {
    const diff = Math.abs(new Date(candidate).getTime() - target);
    if (diff < bestDiff) {
      bestDiff = diff;
      best = value;
    }
  }
  const sevenDays = 7 * 24 * 60 * 60 * 1000;
  return bestDiff <= sevenDays ? best : null;
}
