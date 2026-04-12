import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, Plus, Trash2, History } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/layout/EmptyState';
import type { InvestmentTxnType } from '@/types/portfolio.types';
import { useTransactions, useDeleteTransaction } from '@/hooks/useTransactions';
import { RecordTransactionDialog } from './RecordTransactionDialog';
import { formatShortDate, formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';

interface TransactionLogProps {
  portfolioId: string;
}

const TYPE_TONE: Record<InvestmentTxnType, string> = {
  BUY: 'bg-positive/10 text-positive',
  BES_CONTRIBUTION: 'bg-positive/10 text-positive',
  DEPOSIT: 'bg-primary/10 text-primary',
  SELL: 'bg-negative/10 text-negative',
  WITHDRAW: 'bg-amber-500/10 text-amber-500',
  REBALANCE: 'bg-muted text-muted-foreground',
};

export function TransactionLog({ portfolioId }: TransactionLogProps) {
  const { t } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);
  const txnQuery = useTransactions(portfolioId);
  const deleteTxn = useDeleteTransaction(portfolioId);
  const transactions = txnQuery.data ?? [];

  const handleDelete = (id: string) => {
    if (!window.confirm(t('transactions.confirmDelete'))) return;
    deleteTxn.mutate(id);
  };

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-3 space-y-0">
        <div>
          <CardTitle className="text-sm font-medium">{t('transactions.title')}</CardTitle>
          <CardDescription className="text-xs mt-0.5">
            {t('transactions.description')}
          </CardDescription>
        </div>
        <Button
          size="sm"
          className="cursor-pointer"
          onClick={() => setDialogOpen(true)}
        >
          <Plus className="w-4 h-4 mr-1.5" />
          {t('transactions.record')}
        </Button>
      </CardHeader>

      <CardContent className="p-0">
        {txnQuery.isLoading && (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        )}

        {txnQuery.isError && (
          <div className="px-6 py-12 text-center">
            <p className="text-sm text-destructive">{t('transactions.failedToLoad')}</p>
          </div>
        )}

        {!txnQuery.isLoading && !txnQuery.isError && transactions.length === 0 && (
          <EmptyState
            icon={History}
            title={t('transactions.emptyTitle')}
            description={t('transactions.emptyDesc')}
            action={
              <Button size="sm" className="cursor-pointer" onClick={() => setDialogOpen(true)}>
                <Plus className="w-4 h-4 mr-1.5" />
                {t('transactions.record')}
              </Button>
            }
          />
        )}

        {!txnQuery.isLoading && !txnQuery.isError && transactions.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-[11px] uppercase tracking-wider text-muted-foreground">
                  <th className="text-left font-medium px-4 py-2.5">{t('transactions.colDate')}</th>
                  <th className="text-left font-medium px-4 py-2.5">{t('transactions.colType')}</th>
                  <th className="text-left font-medium px-4 py-2.5">{t('transactions.colAsset')}</th>
                  <th className="text-right font-medium px-4 py-2.5">{t('transactions.colQty')}</th>
                  <th className="text-right font-medium px-4 py-2.5">{t('transactions.colPrice')}</th>
                  <th className="text-right font-medium px-4 py-2.5">{t('transactions.colAmount')}</th>
                  <th className="text-right font-medium px-4 py-2.5">{t('transactions.colFee')}</th>
                  <th className="px-4 py-2.5" aria-label="actions" />
                </tr>
              </thead>
              <tbody>
                {transactions.map((txn) => (
                  <tr
                    key={txn.id}
                    className="border-b last:border-b-0 hover:bg-accent/30 transition-colors"
                  >
                    <td className="px-4 py-2.5 text-muted-foreground whitespace-nowrap">
                      {formatShortDate(txn.txnDate)}
                    </td>
                    <td className="px-4 py-2.5">
                      <span
                        className={cn(
                          'inline-block text-[10px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded',
                          TYPE_TONE[txn.txnType]
                        )}
                      >
                        {t(`transactions.type.${txn.txnType}`)}
                      </span>
                    </td>
                    <td className="px-4 py-2.5">
                      <div className="flex flex-col">
                        <span className="font-medium">{txn.assetSymbol ?? '--'}</span>
                        {txn.assetName && (
                          <span className="text-xs text-muted-foreground truncate max-w-[200px]">
                            {txn.assetName}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono tabular-nums">
                      {txn.quantity}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono tabular-nums text-muted-foreground">
                      {formatTRY(txn.priceTry, true)}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono tabular-nums">
                      {formatTRY(txn.amountTry, true)}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono tabular-nums text-muted-foreground">
                      {txn.feeTry > 0 ? formatTRY(txn.feeTry, true) : '--'}
                    </td>
                    <td className="px-4 py-2.5 text-right">
                      <button
                        type="button"
                        onClick={() => handleDelete(txn.id)}
                        disabled={deleteTxn.isPending}
                        title={t('common.delete')}
                        className="w-7 h-7 rounded-md text-muted-foreground hover:text-destructive hover:bg-destructive/10 inline-flex items-center justify-center transition-colors cursor-pointer disabled:opacity-50"
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

      <RecordTransactionDialog
        open={dialogOpen}
        portfolioId={portfolioId}
        onOpenChange={setDialogOpen}
      />
    </Card>
  );
}
