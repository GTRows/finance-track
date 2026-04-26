import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { Loader2, Search, Check } from 'lucide-react';
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
import type { Asset, InvestmentTxnType } from '@/types/portfolio.types';
import type { ApiError } from '@/types/auth.types';
import { useAssets } from '@/hooks/useAssets';
import { useRecordTransaction } from '@/hooks/useTransactions';
import { cn } from '@/lib/utils';
import { formatTRY } from '@/utils/formatters';

interface RecordTransactionDialogProps {
  open: boolean;
  portfolioId: string;
  onOpenChange: (open: boolean) => void;
}

const TXN_TYPES: InvestmentTxnType[] = [
  'BUY',
  'SELL',
  'DEPOSIT',
  'WITHDRAW',
  'REBALANCE',
  'BES_CONTRIBUTION',
];

const todayIso = () => new Date().toISOString().slice(0, 10);

export function RecordTransactionDialog({
  open,
  portfolioId,
  onOpenChange,
}: RecordTransactionDialogProps) {
  const { t } = useTranslation();
  const { data: assets, isLoading: assetsLoading } = useAssets();
  const record = useRecordTransaction(portfolioId);

  const [txnType, setTxnType] = useState<InvestmentTxnType>('BUY');
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<Asset | null>(null);
  const [quantity, setQuantity] = useState('');
  const [price, setPrice] = useState('');
  const [fee, setFee] = useState('');
  const [txnDate, setTxnDate] = useState(todayIso());
  const [notes, setNotes] = useState('');
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
    setTxnType('BUY');
    setSearch('');
    setSelected(null);
    setQuantity('');
    setPrice('');
    setFee('');
    setTxnDate(todayIso());
    setNotes('');
    setError(null);
  };

  const handleClose = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const parseDecimal = (raw: string) => Number(raw.replace(',', '.'));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!selected) {
      setError(t('transactions.selectAssetError'));
      return;
    }
    const qty = parseDecimal(quantity);
    if (!Number.isFinite(qty) || qty <= 0) {
      setError(t('transactions.quantityError'));
      return;
    }
    const priceNum = parseDecimal(price);
    if (!Number.isFinite(priceNum) || priceNum < 0) {
      setError(t('transactions.priceError'));
      return;
    }
    const feeNum = fee.trim() === '' ? 0 : parseDecimal(fee);
    if (!Number.isFinite(feeNum) || feeNum < 0) {
      setError(t('transactions.feeError'));
      return;
    }

    try {
      await record.mutateAsync({
        assetId: selected.id,
        txnType,
        quantity: qty,
        priceTry: priceNum,
        feeTry: feeNum,
        txnDate,
        notes: notes.trim() || undefined,
      });
      handleClose(false);
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error || t('transactions.failedToRecord'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>{t('transactions.dialogTitle')}</DialogTitle>
          <DialogDescription>{t('transactions.dialogDescription')}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label>{t('transactions.typeLabel')}</Label>
            <div className="flex flex-wrap gap-1.5">
              {TXN_TYPES.map((type) => {
                const active = txnType === type;
                return (
                  <button
                    key={type}
                    type="button"
                    onClick={() => setTxnType(type)}
                    className={cn(
                      'h-8 px-3 rounded-md border text-xs font-medium transition-colors cursor-pointer',
                      active
                        ? 'border-primary bg-primary/10 text-primary'
                        : 'border-input text-muted-foreground hover:text-foreground hover:bg-accent'
                    )}
                  >
                    {t(`transactions.type.${type}`)}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="txn-asset-search">{t('transactions.assetLabel')}</Label>
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
              <Input
                id="txn-asset-search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={t('holdings.assetSearchPlaceholder')}
                className="pl-9"
                autoComplete="off"
              />
            </div>

            <div className="mt-2 max-h-[180px] overflow-y-auto rounded-md border bg-background/50">
              {assetsLoading && (
                <div className="flex items-center justify-center py-6">
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
                      onClick={() => {
                        setSelected(a);
                        if (a.price && !price) setPrice(String(a.price));
                      }}
                      className={cn(
                        'w-full flex items-center gap-3 px-3 py-2 text-left transition-colors border-b last:border-b-0 cursor-pointer',
                        active ? 'bg-primary/10' : 'hover:bg-accent/50'
                      )}
                    >
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
              <Label htmlFor="txn-quantity">{t('transactions.quantityLabel')}</Label>
              <Input
                id="txn-quantity"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                placeholder="0,00"
                inputMode="decimal"
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="txn-price">{t('transactions.priceLabel')}</Label>
              <Input
                id="txn-price"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                placeholder="0,00"
                inputMode="decimal"
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="txn-fee">{t('transactions.feeLabel')}</Label>
              <Input
                id="txn-fee"
                value={fee}
                onChange={(e) => setFee(e.target.value)}
                placeholder={t('common.optional')}
                inputMode="decimal"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="txn-date">{t('transactions.dateLabel')}</Label>
              <Input
                id="txn-date"
                type="date"
                value={txnDate}
                onChange={(e) => setTxnDate(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="txn-notes">{t('transactions.notesLabel')}</Label>
            <Input
              id="txn-notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={t('common.optional')}
              maxLength={500}
            />
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
              disabled={record.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button type="submit" className="cursor-pointer" disabled={record.isPending}>
              {record.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  {t('transactions.recording')}
                </>
              ) : (
                t('transactions.record')
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
