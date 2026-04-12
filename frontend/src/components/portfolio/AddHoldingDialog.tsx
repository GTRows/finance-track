import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2, Search, Check } from 'lucide-react';
import { AxiosError } from 'axios';
import type { ApiError } from '@/types/auth.types';
import type { Asset } from '@/types/portfolio.types';
import { useAssets } from '@/hooks/useAssets';
import { useAddHolding } from '@/hooks/useHoldings';
import { cn } from '@/lib/utils';
import { formatTRY } from '@/utils/formatters';

interface AddHoldingDialogProps {
  open: boolean;
  portfolioId: string;
  onOpenChange: (open: boolean) => void;
}

export function AddHoldingDialog({ open, portfolioId, onOpenChange }: AddHoldingDialogProps) {
  const { t } = useTranslation();
  const { data: assets, isLoading: assetsLoading } = useAssets();
  const addHolding = useAddHolding(portfolioId);

  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<Asset | null>(null);
  const [quantity, setQuantity] = useState('');
  const [avgCost, setAvgCost] = useState('');
  const [error, setError] = useState<string | null>(null);

  const filtered = useMemo(() => {
    if (!assets) return [];
    const q = search.trim().toLowerCase();
    if (!q) return assets;
    return assets.filter(
      (a) =>
        a.symbol.toLowerCase().includes(q) ||
        a.name.toLowerCase().includes(q) ||
        a.assetType.toLowerCase().includes(q)
    );
  }, [assets, search]);

  const reset = () => {
    setSearch('');
    setSelected(null);
    setQuantity('');
    setAvgCost('');
    setError(null);
  };

  const handleClose = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!selected) {
      setError(t('holdings.selectAsset'));
      return;
    }
    const qty = Number(quantity.replace(',', '.'));
    if (!Number.isFinite(qty) || qty <= 0) {
      setError(t('holdings.quantityError'));
      return;
    }
    const cost = avgCost.trim() === '' ? undefined : Number(avgCost.replace(',', '.'));
    if (cost !== undefined && (!Number.isFinite(cost) || cost < 0)) {
      setError(t('holdings.avgCostError'));
      return;
    }

    try {
      await addHolding.mutateAsync({
        assetId: selected.id,
        quantity: qty,
        avgCostTry: cost,
      });
      handleClose(false);
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error || t('holdings.failedToAdd'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>{t('holdings.dialogTitle')}</DialogTitle>
          <DialogDescription>
            {t('holdings.dialogDescription')}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="asset-search">{t('holdings.assetLabel')}</Label>
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
              <Input
                id="asset-search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t('holdings.assetSearchPlaceholder')}
                className="pl-9"
                autoComplete="off"
              />
            </div>

            <div className="mt-2 max-h-[220px] overflow-y-auto rounded-md border bg-background/50">
              {assetsLoading && (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />
                </div>
              )}

              {!assetsLoading && filtered.length === 0 && (
                <p className="px-3 py-6 text-center text-xs text-muted-foreground">
                  {t('holdings.assetNoMatch')}
                </p>
              )}

              {!assetsLoading &&
                filtered.map((a) => {
                  const active = selected?.id === a.id;
                  return (
                    <button
                      key={a.id}
                      type="button"
                      onClick={() => setSelected(a)}
                      className={cn(
                        'w-full flex items-center gap-3 px-3 py-2.5 text-left transition-colors border-b last:border-b-0 cursor-pointer',
                        active ? 'bg-primary/10' : 'hover:bg-accent/50'
                      )}
                    >
                      <div
                        className={cn(
                          'w-7 h-7 rounded-md flex items-center justify-center text-[10px] font-semibold uppercase tracking-tight flex-shrink-0',
                          active
                            ? 'bg-primary/20 text-primary'
                            : 'bg-muted text-muted-foreground'
                        )}
                      >
                        {a.symbol.slice(0, 3)}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium truncate">{a.symbol}</span>
                          <span className="text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-muted text-muted-foreground">
                            {a.assetType}
                          </span>
                        </div>
                        <p className="text-xs text-muted-foreground truncate">{a.name}</p>
                      </div>
                      <div className="text-right flex-shrink-0">
                        <p className="text-xs font-mono text-muted-foreground">
                          {a.price != null ? formatTRY(a.price, true) : '--'}
                        </p>
                      </div>
                      {active && (
                        <Check className="w-4 h-4 text-primary flex-shrink-0" strokeWidth={3} />
                      )}
                    </button>
                  );
                })}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="holding-quantity">{t('holdings.quantityLabel')}</Label>
              <Input
                id="holding-quantity"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                placeholder="0,00"
                inputMode="decimal"
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="holding-cost">{t('holdings.avgCostLabel')}</Label>
              <Input
                id="holding-cost"
                value={avgCost}
                onChange={(e) => setAvgCost(e.target.value)}
                placeholder={t('common.optional')}
                inputMode="decimal"
              />
            </div>
          </div>

          {error && (
            <div className="rounded-md bg-destructive/10 border border-destructive/20 px-3 py-2.5">
              <p className="text-sm text-destructive">{error}</p>
            </div>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              className="cursor-pointer"
              onClick={() => handleClose(false)}
              disabled={addHolding.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button type="submit" className="cursor-pointer" disabled={addHolding.isPending}>
              {addHolding.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  {t('holdings.adding')}
                </>
              ) : (
                t('holdings.addHolding')
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
