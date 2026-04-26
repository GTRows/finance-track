import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { Loader2, Plus, Trash2, Landmark } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { EmptyState } from '@/components/layout/EmptyState';
import { useHoldings } from '@/hooks/useHoldings';
import { useDividends, useRecordDividend, useDeleteDividend } from '@/hooks/useDividends';
import { formatShortDate, formatTRY } from '@/utils/formatters';
import type { ApiError } from '@/types/auth.types';
import { cn } from '@/lib/utils';

interface DividendLedgerProps {
  portfolioId: string;
}

const todayIso = () => new Date().toISOString().slice(0, 10);

export function DividendLedger({ portfolioId }: DividendLedgerProps) {
  const { t } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);
  const dividendsQuery = useDividends(portfolioId);
  const deleteDividend = useDeleteDividend(portfolioId);
  const dividends = useMemo(() => dividendsQuery.data ?? [], [dividendsQuery.data]);

  const totalTry = useMemo(
    () => dividends.reduce((acc, d) => acc + d.netAmountTry, 0),
    [dividends],
  );

  const handleDelete = (id: string) => {
    if (!window.confirm(t('dividends.confirmDelete'))) return;
    deleteDividend.mutate(id);
  };

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-3 space-y-0">
        <div>
          <CardTitle className="text-sm font-medium flex items-center gap-2">
            <Landmark className="w-4 h-4 text-primary" />
            {t('dividends.title')}
          </CardTitle>
          <CardDescription className="text-xs mt-0.5">
            {t('dividends.description')}
          </CardDescription>
        </div>
        <div className="flex items-center gap-3">
          {dividends.length > 0 && (
            <div className="text-right">
              <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                {t('dividends.totalNet')}
              </p>
              <p className="text-sm font-mono tabular-nums font-semibold text-positive">
                {formatTRY(totalTry)}
              </p>
            </div>
          )}
          <Button size="sm" className="cursor-pointer" onClick={() => setDialogOpen(true)}>
            <Plus className="w-4 h-4 mr-2" />
            {t('dividends.record')}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="p-0">
        {dividendsQuery.isLoading && (
          <div className="flex items-center justify-center py-10">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        )}

        {!dividendsQuery.isLoading && dividends.length === 0 && (
          <EmptyState
            icon={Landmark}
            title={t('dividends.emptyTitle')}
            description={t('dividends.emptyDesc')}
          />
        )}

        {!dividendsQuery.isLoading && dividends.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-y border-border/60 bg-card/40 text-[11px] uppercase tracking-wider text-muted-foreground">
                  <th className="text-left font-medium py-2.5 px-4">
                    {t('dividends.colDate')}
                  </th>
                  <th className="text-left font-medium py-2.5 px-4">
                    {t('dividends.colAsset')}
                  </th>
                  <th className="text-right font-medium py-2.5 px-4">
                    {t('dividends.colGross')}
                  </th>
                  <th className="text-right font-medium py-2.5 px-4">
                    {t('dividends.colTax')}
                  </th>
                  <th className="text-right font-medium py-2.5 px-4">
                    {t('dividends.colNet')}
                  </th>
                  <th className="text-right font-medium py-2.5 px-4">
                    {t('dividends.colNetTry')}
                  </th>
                  <th className="text-right font-medium py-2.5 px-4 w-10" />
                </tr>
              </thead>
              <tbody>
                {dividends.map((d) => (
                  <tr
                    key={d.id}
                    className="border-b border-border/40 last:border-b-0 hover:bg-accent/30"
                  >
                    <td className="py-2.5 px-4 font-mono tabular-nums text-xs text-muted-foreground">
                      {formatShortDate(d.paymentDate)}
                    </td>
                    <td className="py-2.5 px-4">
                      <div className="flex flex-col">
                        <span className="font-medium">{d.assetSymbol ?? '—'}</span>
                        {d.assetName && (
                          <span className="text-[11px] text-muted-foreground">
                            {d.assetName}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono tabular-nums">
                      {formatAmount(d.grossAmount, d.currency)}
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono tabular-nums text-muted-foreground">
                      {formatAmount(d.withholdingTax, d.currency)}
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono tabular-nums">
                      {formatAmount(d.netAmount, d.currency)}
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono tabular-nums text-positive">
                      {formatTRY(d.netAmountTry)}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      <button
                        type="button"
                        onClick={() => handleDelete(d.id)}
                        className={cn(
                          'p-1.5 rounded-md text-muted-foreground transition-colors cursor-pointer',
                          'hover:bg-destructive/10 hover:text-destructive',
                        )}
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>

      <RecordDividendDialog
        open={dialogOpen}
        portfolioId={portfolioId}
        onOpenChange={setDialogOpen}
      />
    </Card>
  );
}

function formatAmount(value: number, currency: string): string {
  const abs = Math.abs(value);
  const formatted = abs.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  if (currency === 'TRY') return `${value < 0 ? '-' : ''}${formatted} ₺`;
  return `${value < 0 ? '-' : ''}${formatted} ${currency}`;
}

interface RecordDividendDialogProps {
  open: boolean;
  portfolioId: string;
  onOpenChange: (open: boolean) => void;
}

function RecordDividendDialog({ open, portfolioId, onOpenChange }: RecordDividendDialogProps) {
  const { t } = useTranslation();
  const { data: holdings, isLoading: holdingsLoading } = useHoldings(portfolioId);
  const record = useRecordDividend(portfolioId);

  const [assetId, setAssetId] = useState('');
  const [grossAmount, setGrossAmount] = useState('');
  const [withholdingTax, setWithholdingTax] = useState('');
  const [currency, setCurrency] = useState('TRY');
  const [paymentDate, setPaymentDate] = useState(todayIso());
  const [exDividendDate, setExDividendDate] = useState('');
  const [amountPerShare, setAmountPerShare] = useState('');
  const [shares, setShares] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setAssetId('');
    setGrossAmount('');
    setWithholdingTax('');
    setCurrency('TRY');
    setPaymentDate(todayIso());
    setExDividendDate('');
    setAmountPerShare('');
    setShares('');
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

    if (!assetId) {
      setError(t('dividends.assetRequired'));
      return;
    }
    const gross = parseDecimal(grossAmount);
    if (!Number.isFinite(gross) || gross <= 0) {
      setError(t('dividends.grossError'));
      return;
    }
    const tax = withholdingTax.trim() === '' ? 0 : parseDecimal(withholdingTax);
    if (!Number.isFinite(tax) || tax < 0) {
      setError(t('dividends.taxError'));
      return;
    }
    if (tax > gross) {
      setError(t('dividends.taxExceedsGross'));
      return;
    }

    try {
      await record.mutateAsync({
        assetId,
        grossAmount: gross,
        withholdingTax: tax,
        currency: currency.trim().toUpperCase() || 'TRY',
        paymentDate,
        exDividendDate: exDividendDate.trim() || undefined,
        amountPerShare: amountPerShare.trim() ? parseDecimal(amountPerShare) : undefined,
        shares: shares.trim() ? parseDecimal(shares) : undefined,
        notes: notes.trim() || undefined,
      });
      handleClose(false);
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error || t('dividends.failedToRecord'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>{t('dividends.dialogTitle')}</DialogTitle>
          <DialogDescription>{t('dividends.dialogDescription')}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label>{t('dividends.assetLabel')}</Label>
            <div className="max-h-[180px] overflow-y-auto rounded-md border bg-background/50">
              {holdingsLoading && (
                <div className="flex items-center justify-center py-6">
                  <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />
                </div>
              )}
              {!holdingsLoading && (holdings?.length ?? 0) === 0 && (
                <p className="px-3 py-6 text-center text-xs text-muted-foreground">
                  {t('dividends.noHoldings')}
                </p>
              )}
              {!holdingsLoading &&
                (holdings ?? []).map((h) => {
                  const active = assetId === h.assetId;
                  return (
                    <button
                      key={h.id}
                      type="button"
                      onClick={() => setAssetId(h.assetId)}
                      className={cn(
                        'w-full flex items-center gap-3 px-3 py-2 text-left transition-colors border-b last:border-b-0 cursor-pointer',
                        active ? 'bg-primary/10' : 'hover:bg-accent/50',
                      )}
                    >
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium truncate">{h.assetSymbol}</p>
                        <p className="text-xs text-muted-foreground truncate">
                          {h.assetName}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="text-xs font-mono text-muted-foreground">
                          {h.quantity.toLocaleString('tr-TR', {
                            maximumFractionDigits: 4,
                          })}
                        </p>
                      </div>
                    </button>
                  );
                })}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="div-gross">{t('dividends.grossLabel')}</Label>
              <Input
                id="div-gross"
                value={grossAmount}
                onChange={(e) => setGrossAmount(e.target.value)}
                placeholder="0,00"
                inputMode="decimal"
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="div-tax">{t('dividends.taxLabel')}</Label>
              <Input
                id="div-tax"
                value={withholdingTax}
                onChange={(e) => setWithholdingTax(e.target.value)}
                placeholder={t('common.optional')}
                inputMode="decimal"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="div-currency">{t('dividends.currencyLabel')}</Label>
              <Input
                id="div-currency"
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                maxLength={10}
                className="uppercase"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="div-payment">{t('dividends.paymentDateLabel')}</Label>
              <Input
                id="div-payment"
                type="date"
                value={paymentDate}
                onChange={(e) => setPaymentDate(e.target.value)}
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="div-ex">{t('dividends.exDividendDateLabel')}</Label>
              <Input
                id="div-ex"
                type="date"
                value={exDividendDate}
                onChange={(e) => setExDividendDate(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="div-shares">{t('dividends.sharesLabel')}</Label>
              <Input
                id="div-shares"
                value={shares}
                onChange={(e) => setShares(e.target.value)}
                placeholder={t('common.optional')}
                inputMode="decimal"
              />
            </div>
            <div className="space-y-1.5 col-span-2">
              <Label htmlFor="div-per-share">{t('dividends.amountPerShareLabel')}</Label>
              <Input
                id="div-per-share"
                value={amountPerShare}
                onChange={(e) => setAmountPerShare(e.target.value)}
                placeholder={t('common.optional')}
                inputMode="decimal"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="div-notes">{t('dividends.notesLabel')}</Label>
            <Input
              id="div-notes"
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
                  {t('dividends.recording')}
                </>
              ) : (
                t('dividends.record')
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
