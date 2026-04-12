import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, Trash2 } from 'lucide-react';
import type { Holding } from '@/types/portfolio.types';
import { formatTRY, formatPercent } from '@/utils/formatters';
import { useDeleteHolding } from '@/hooks/useHoldings';
import { cn } from '@/lib/utils';

interface HoldingsTableProps {
  portfolioId: string;
  holdings: Holding[];
}

type FlashDirection = 'up' | 'down' | null;

export function HoldingsTable({ portfolioId, holdings }: HoldingsTableProps) {
  const { t } = useTranslation();
  const deleteMutation = useDeleteHolding(portfolioId);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  const previousPrices = useRef<Record<string, number | null>>({});
  const [flashes, setFlashes] = useState<Record<string, FlashDirection>>({});

  useEffect(() => {
    const next: Record<string, FlashDirection> = {};
    let changed = false;

    for (const h of holdings) {
      const prev = previousPrices.current[h.id];
      const current = h.currentPriceTry;
      if (prev != null && current != null && prev !== current) {
        next[h.id] = current > prev ? 'up' : 'down';
        changed = true;
      }
      previousPrices.current[h.id] = current;
    }

    if (changed) {
      setFlashes(next);
      const timer = window.setTimeout(() => setFlashes({}), 1200);
      return () => window.clearTimeout(timer);
    }
  }, [holdings]);

  const handleDelete = async (holdingId: string) => {
    if (confirmingId !== holdingId) {
      setConfirmingId(holdingId);
      return;
    }
    try {
      await deleteMutation.mutateAsync(holdingId);
    } finally {
      setConfirmingId(null);
    }
  };

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-xs uppercase tracking-wider text-muted-foreground">
            <th className="text-left font-medium px-5 py-3">{t('holdings.tableAsset')}</th>
            <th className="text-right font-medium px-3 py-3">{t('holdings.tableQuantity')}</th>
            <th className="text-right font-medium px-3 py-3">{t('holdings.tableAvgCost')}</th>
            <th className="text-right font-medium px-3 py-3">{t('holdings.tablePrice')}</th>
            <th className="text-right font-medium px-3 py-3">{t('holdings.tableValue')}</th>
            <th className="text-right font-medium px-3 py-3">{t('holdings.tablePnl')}</th>
            <th className="w-12 px-3 py-3"></th>
          </tr>
        </thead>
        <tbody>
          {holdings.map((h) => {
            const isPositive = (h.pnlTry ?? 0) >= 0;
            const pnlClass = h.pnlTry == null
              ? 'text-muted-foreground'
              : isPositive
                ? 'text-positive'
                : 'text-negative';
            const isConfirming = confirmingId === h.id;
            const isDeleting = deleteMutation.isPending && deleteMutation.variables === h.id;
            const flashClass = flashes[h.id] === 'up'
              ? 'flash-up'
              : flashes[h.id] === 'down'
                ? 'flash-down'
                : '';

            return (
              <tr
                key={h.id}
                className="group border-b last:border-b-0 hover:bg-accent/30 transition-colors"
              >
                <td className="px-5 py-3.5">
                  <div className="flex flex-col">
                    <span className="font-medium">{h.assetSymbol}</span>
                    <span className="text-xs text-muted-foreground truncate max-w-[200px]">
                      {h.assetName}
                    </span>
                  </div>
                </td>
                <td className="px-3 py-3.5 text-right font-mono tabular-nums">
                  {formatQuantity(h.quantity)}
                </td>
                <td className="px-3 py-3.5 text-right font-mono tabular-nums text-muted-foreground">
                  {h.avgCostTry != null ? formatTRY(h.avgCostTry, true) : '--'}
                </td>
                <td className={cn('px-3 py-3.5 text-right font-mono tabular-nums', flashClass)}>
                  {h.currentPriceTry != null ? formatTRY(h.currentPriceTry, true) : '--'}
                </td>
                <td className={cn('px-3 py-3.5 text-right font-mono tabular-nums font-medium', flashClass)}>
                  {h.currentValueTry != null ? formatTRY(h.currentValueTry) : '--'}
                </td>
                <td className={cn('px-3 py-3.5 text-right font-mono tabular-nums', pnlClass)}>
                  {h.pnlTry != null ? (
                    <div className="flex flex-col items-end">
                      <span>{formatTRY(h.pnlTry, true)}</span>
                      {h.pnlPercent != null && (
                        <span className="text-xs">{formatPercent(h.pnlPercent)}</span>
                      )}
                    </div>
                  ) : (
                    '--'
                  )}
                </td>
                <td className="px-3 py-3.5">
                  <button
                    type="button"
                    onClick={() => handleDelete(h.id)}
                    disabled={isDeleting}
                    title={isConfirming ? t('common.confirmAgain') : t('holdings.deleteHolding')}
                    className={cn(
                      'w-8 h-8 rounded-md flex items-center justify-center transition-colors cursor-pointer',
                      isConfirming
                        ? 'bg-destructive/15 text-destructive opacity-100'
                        : 'text-muted-foreground hover:text-destructive hover:bg-destructive/10 opacity-0 group-hover:opacity-100 focus:opacity-100',
                      isDeleting && 'opacity-50 cursor-not-allowed'
                    )}
                  >
                    {isDeleting ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Trash2 className="w-4 h-4" />
                    )}
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/** Formats a quantity, hiding trailing zeros but keeping precision for crypto. */
function formatQuantity(value: number): string {
  if (value === 0) return '0';
  if (Math.abs(value) >= 1000) {
    return new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 2 }).format(value);
  }
  return new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 8 }).format(value);
}
