import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useRisk } from '@/hooks/useRisk';
import { formatPercent } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import { Activity, TrendingDown, TrendingUp, Gauge, Waves, Hourglass } from 'lucide-react';

interface Props {
  portfolioId: string;
}

export function PortfolioRiskMetrics({ portfolioId }: Props) {
  const { t, i18n } = useTranslation();
  const [rfInput, setRfInput] = useState('0');

  const rfNum = Number(rfInput.replace(',', '.'));
  const riskFreeRate = Number.isFinite(rfNum) ? rfNum / 100 : 0;

  const { data, isLoading } = useRisk(portfolioId, riskFreeRate);

  if (isLoading || !data) return null;

  const locale = i18n.resolvedLanguage === 'tr' ? 'tr-TR' : 'en-US';

  if (!data.sufficientData) {
    return (
      <Card>
        <CardContent className="p-5 flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-muted flex items-center justify-center shrink-0">
            <Hourglass className="w-4 h-4 text-muted-foreground" />
          </div>
          <div className="space-y-0.5">
            <h3 className="text-sm font-medium">{t('risk.title')}</h3>
            <p className="text-[11px] text-muted-foreground">
              {t('risk.waiting', { count: Math.max(20 - data.snapshotCount, 1) })}
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  const sharpe = data.sharpeRatio ?? 0;
  const sharpeTone =
    sharpe >= 1 ? 'emerald' : sharpe >= 0 ? 'amber' : 'rose';
  const sharpeLabel =
    sharpe >= 1 ? t('risk.sharpeGood') : sharpe >= 0 ? t('risk.sharpeOk') : t('risk.sharpeBad');

  const periodLabel =
    data.periodStart && data.periodEnd
      ? `${new Date(data.periodStart).toLocaleDateString(locale, {
          month: 'short',
          day: 'numeric',
        })} -- ${new Date(data.periodEnd).toLocaleDateString(locale, {
          month: 'short',
          day: 'numeric',
          year: 'numeric',
        })}`
      : '';

  return (
    <Card className="overflow-hidden">
      <div className="px-5 py-4 border-b border-border/60 flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="w-9 h-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
            <Activity className="w-4 h-4 text-primary" />
          </div>
          <div className="space-y-0.5">
            <h3 className="text-sm font-medium">{t('risk.title')}</h3>
            <p className="text-[11px] text-muted-foreground">
              {periodLabel} -- {t('risk.daysSample', { count: data.snapshotCount })}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <span className="text-[10px] text-muted-foreground uppercase tracking-wider">
            {t('risk.riskFreeRate')}
          </span>
          <div className="relative">
            <Input
              value={rfInput}
              onChange={(e) => setRfInput(e.target.value)}
              className="h-7 w-20 text-xs font-mono tabular-nums pr-6"
              inputMode="decimal"
            />
            <span className="absolute right-2 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground pointer-events-none">
              %
            </span>
          </div>
        </div>
      </div>

      <CardContent className="p-5">
        {/* Hero: Sharpe ratio */}
        <div
          className={cn(
            'rounded-xl p-5 mb-4 relative overflow-hidden',
            sharpeTone === 'emerald' && 'bg-emerald-500/[0.06] border border-emerald-500/20',
            sharpeTone === 'amber' && 'bg-amber-500/[0.06] border border-amber-500/20',
            sharpeTone === 'rose' && 'bg-rose-500/[0.06] border border-rose-500/20'
          )}
        >
          <div className="flex items-end justify-between gap-4">
            <div className="space-y-1">
              <p className="text-[10px] uppercase tracking-widest text-muted-foreground">
                {t('risk.sharpe')}
              </p>
              <p
                className={cn(
                  'text-4xl font-semibold font-mono tabular-nums tracking-tight',
                  sharpeTone === 'emerald' && 'text-emerald-400',
                  sharpeTone === 'amber' && 'text-amber-400',
                  sharpeTone === 'rose' && 'text-rose-400'
                )}
              >
                {(data.sharpeRatio ?? 0).toFixed(2)}
              </p>
              <p className="text-[11px] text-muted-foreground">{sharpeLabel}</p>
            </div>
            <Gauge
              className={cn(
                'w-14 h-14 opacity-20',
                sharpeTone === 'emerald' && 'text-emerald-400',
                sharpeTone === 'amber' && 'text-amber-400',
                sharpeTone === 'rose' && 'text-rose-400'
              )}
              strokeWidth={1.25}
            />
          </div>
        </div>

        {/* Metric tiles */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <MetricTile
            label={t('risk.totalReturn')}
            value={data.totalReturn}
            signed
          />
          <MetricTile
            label={t('risk.volatility')}
            value={data.annualVolatility}
            icon={<Waves className="w-3 h-3" />}
            tone="neutral"
          />
          <MetricTile
            label={t('risk.maxDrawdown')}
            value={data.maxDrawdown}
            icon={<TrendingDown className="w-3 h-3" />}
            tone="rose"
          />
          <MetricTile
            label={t('risk.bestWorst')}
            bestDay={data.bestDay}
            worstDay={data.worstDay}
          />
        </div>
      </CardContent>
    </Card>
  );
}

interface TileProps {
  label: string;
  value?: number | null;
  signed?: boolean;
  icon?: React.ReactNode;
  tone?: 'neutral' | 'rose';
  bestDay?: number | null;
  worstDay?: number | null;
}

function MetricTile({ label, value, signed, icon, tone = 'neutral', bestDay, worstDay }: TileProps) {
  const isRange = bestDay != null || worstDay != null;

  return (
    <div className="rounded-lg border border-border/60 bg-muted/30 px-3 py-2.5 space-y-1">
      <div className="flex items-center gap-1 text-[10px] uppercase tracking-wider text-muted-foreground">
        {icon}
        {label}
      </div>
      {isRange ? (
        <div className="flex items-center gap-2 font-mono tabular-nums text-[13px]">
          <span className="text-emerald-400 inline-flex items-center gap-0.5">
            <TrendingUp className="w-3 h-3" />
            {formatPercent(bestDay ?? 0)}
          </span>
          <span className="text-rose-400 inline-flex items-center gap-0.5">
            <TrendingDown className="w-3 h-3" />
            {formatPercent(worstDay ?? 0)}
          </span>
        </div>
      ) : (
        <p
          className={cn(
            'text-lg font-semibold font-mono tabular-nums tracking-tight',
            signed && value != null
              ? value >= 0
                ? 'text-emerald-400'
                : 'text-rose-400'
              : tone === 'rose'
                ? 'text-rose-400'
                : 'text-foreground'
          )}
        >
          {value != null ? formatPercent(value) : '--'}
        </p>
      )}
    </div>
  );
}
