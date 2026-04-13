import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { ArrowUp, ArrowDown, Minus, Activity } from 'lucide-react';
import { useLivePricesStore, type LivePrice } from '@/store/livePrices.store';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';

type Direction = 'up' | 'down' | 'flat';

function direction(p: LivePrice): Direction {
  if (p.previousPrice == null) return 'flat';
  if (p.price > p.previousPrice) return 'up';
  if (p.price < p.previousPrice) return 'down';
  return 'flat';
}

export function LivePriceTicker() {
  const { t } = useTranslation();
  const prices = useLivePricesStore((s) => s.prices);
  const publishedAt = useLivePricesStore((s) => s.publishedAt);

  const rows = useMemo(() => {
    return Object.values(prices)
      .filter((p) => p.price > 0)
      .sort((a, b) => a.symbol.localeCompare(b.symbol));
  }, [prices]);

  if (rows.length === 0) {
    return null;
  }

  return (
    <div className="rounded-lg border bg-card/60 overflow-hidden">
      <div className="flex items-center justify-between px-4 py-2 border-b bg-muted/40">
        <div className="flex items-center gap-2">
          <Activity className="w-3.5 h-3.5 text-primary" />
          <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
            {t('ticker.label')}
          </span>
          <span className="live-indicator text-[11px]">{t('holdings.live')}</span>
        </div>
        {publishedAt && (
          <span className="text-[10px] font-mono text-muted-foreground tabular-nums">
            {new Date(publishedAt).toLocaleTimeString()}
          </span>
        )}
      </div>

      <div className="overflow-x-auto">
        <div className="flex gap-4 px-4 py-2.5 min-w-max">
          {rows.map((p) => {
            const dir = direction(p);
            const DirIcon = dir === 'up' ? ArrowUp : dir === 'down' ? ArrowDown : Minus;
            const tone =
              dir === 'up'
                ? 'text-positive'
                : dir === 'down'
                  ? 'text-negative'
                  : 'text-muted-foreground';
            return (
              <div
                key={p.symbol}
                className="flex items-center gap-2 pr-4 border-r last:border-r-0 border-border/40"
              >
                <span className="text-xs font-semibold tracking-wide">{p.symbol}</span>
                <span className="text-xs font-mono tabular-nums">
                  {formatTRY(p.price, true)}
                </span>
                <span className={cn('flex items-center text-[11px] font-medium', tone)}>
                  <DirIcon className="w-3 h-3" strokeWidth={3} />
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
