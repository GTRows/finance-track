import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';
import type { Holding } from '@/types/portfolio.types';
import { formatTRY, formatPercent } from '@/utils/formatters';

interface AllocationChartProps {
  holdings: Holding[];
}

/**
 * Stable palette tuned for the dark theme. Colors are indexed in order and
 * hand-picked for good contrast between adjacent slices.
 */
const PALETTE = [
  '#22d3ee', // cyan
  '#a78bfa', // violet
  '#f472b6', // pink
  '#fbbf24', // amber
  '#34d399', // emerald
  '#60a5fa', // blue
  '#f87171', // red
  '#c084fc', // purple
  '#facc15', // yellow
  '#2dd4bf', // teal
];

interface Slice {
  id: string;
  symbol: string;
  value: number;
  weight: number;
  color: string;
}

export function AllocationChart({ holdings }: AllocationChartProps) {
  const { t } = useTranslation();

  const { slices, total } = useMemo(() => {
    const valued = holdings
      .map((h) => ({ id: h.id, symbol: h.assetSymbol, value: h.currentValueTry ?? 0 }))
      .filter((h) => h.value > 0)
      .sort((a, b) => b.value - a.value);

    const totalValue = valued.reduce((acc, h) => acc + h.value, 0);

    const built: Slice[] = valued.map((h, idx) => ({
      id: h.id,
      symbol: h.symbol,
      value: h.value,
      weight: totalValue > 0 ? h.value / totalValue : 0,
      color: PALETTE[idx % PALETTE.length],
    }));

    return { slices: built, total: totalValue };
  }, [holdings]);

  if (slices.length === 0) {
    return null;
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-[220px_1fr] gap-6 items-center">
      {/* Donut */}
      <div className="relative h-[220px] w-[220px] mx-auto md:mx-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={slices}
              dataKey="value"
              nameKey="symbol"
              cx="50%"
              cy="50%"
              innerRadius={64}
              outerRadius={96}
              paddingAngle={slices.length > 1 ? 2 : 0}
              stroke="hsl(var(--card))"
              strokeWidth={2}
              isAnimationActive
            >
              {slices.map((s) => (
                <Cell key={s.id} fill={s.color} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        {/* Center label */}
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
          <span className="text-[10px] uppercase tracking-wider text-muted-foreground">
            {t('holdings.currentValue')}
          </span>
          <span className="text-base font-semibold font-mono tabular-nums mt-0.5">
            {formatTRY(total)}
          </span>
          <span className="text-[10px] text-muted-foreground mt-0.5">
            {t('holdings.holdingOther', { count: slices.length })}
          </span>
        </div>
      </div>

      {/* Legend */}
      <ul className="space-y-2 max-h-[240px] overflow-y-auto pr-1">
        {slices.map((s) => (
          <li key={s.id} className="flex items-center gap-3 text-sm">
            <span
              className="w-2.5 h-2.5 rounded-sm flex-shrink-0"
              style={{ backgroundColor: s.color }}
            />
            <span className="font-medium truncate flex-1">{s.symbol}</span>
            <span className="font-mono tabular-nums text-muted-foreground text-xs">
              {formatTRY(s.value)}
            </span>
            <span className="font-mono tabular-nums text-xs w-14 text-right">
              {formatPercent(s.weight)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
