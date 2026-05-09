import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, Plus, Trash2, History } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/layout/EmptyState';
import { VirtualizedList } from '@/components/common/VirtualizedList';
import type { InvestmentTxnType, Transaction } from '@/types/portfolio.types';
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

const GRID_COLS =
  'grid-cols-[minmax(0,11ch)_minmax(0,8ch)_minmax(0,1fr)_minmax(0,12ch)_minmax(0,12ch)_minmax(0,12ch)_minmax(0,10ch)_minmax(0,4ch)]';

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

  const renderHeader = () => (
    <div
      role="rowgroup"
      className="border-b text-[11px] uppercase tracking-wider text-muted-foreground"
    >
      <div role="row" className={cn('grid gap-0 px-4 py-2.5', GRID_COLS)}>
        <div role="columnheader" className="text-left font-medium">
          {t('transactions.colDate')}
        </div>
        <div role="columnheader" className="text-left font-medium">
          {t('transactions.colType')}
        </div>
        <div role="columnheader" className="text-left font-medium">
          {t('transactions.colAsset')}
        </div>
        <div role="columnheader" className="text-right font-medium">
          {t('transactions.colQty')}
        </div>
        <div role="columnheader" className="text-right font-medium">
          {t('transactions.colPrice')}
        </div>
        <div role="columnheader" className="text-right font-medium">
          {t('transactions.colAmount')}
        </div>
        <div role="columnheader" className="text-right font-medium">
          {t('transactions.colFee')}
        </div>
        <div role="columnheader" aria-label={t('common.actions')} />
      </div>
    </div>
  );

  const renderRow = (txn: Transaction) => (
    <div
      className={cn(
        'grid gap-0 px-4 py-2.5 border-b last:border-b-0 hover:bg-accent/30 transition-colors',
        GRID_COLS,
      )}
    >
      <div role="cell" className="text-muted-foreground whitespace-nowrap">
        {formatShortDate(txn.txnDate)}
      </div>
      <div role="cell">
        <span
          className={cn(
            'inline-block text-[10px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded',
            TYPE_TONE[txn.txnType],
          )}
        >
          {t(`transactions.type.${txn.txnType}`)}
        </span>
      </div>
      <div role="cell">
        <div className="flex flex-col">
          <span className="font-medium">{txn.assetSymbol ?? '--'}</span>
          {txn.assetName && (
            <span className="text-xs text-muted-foreground truncate max-w-[200px]">
              {txn.assetName}
            </span>
          )}
        </div>
      </div>
      <div role="cell" className="text-right font-mono tabular-nums">
        {txn.quantity}
      </div>
      <div role="cell" className="text-right font-mono tabular-nums text-muted-foreground">
        {formatTRY(txn.priceTry, true)}
      </div>
      <div role="cell" className="text-right font-mono tabular-nums">
        {formatTRY(txn.amountTry, true)}
      </div>
      <div role="cell" className="text-right font-mono tabular-nums text-muted-foreground">
        {txn.feeTry > 0 ? formatTRY(txn.feeTry, true) : '--'}
      </div>
      <div role="cell" className="text-right">
        <button
          type="button"
          onClick={() => handleDelete(txn.id)}
          disabled={deleteTxn.isPending}
          title={t('common.delete')}
          className="w-7 h-7 rounded-md text-muted-foreground hover:text-destructive hover:bg-destructive/10 inline-flex items-center justify-center transition-colors cursor-pointer disabled:opacity-50"
        >
          <Trash2 className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );

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
          <div role="table" aria-label={t('transactions.title')} className="w-full text-sm overflow-x-auto">
            <VirtualizedList<Transaction>
              items={transactions}
              getItemKey={(txn) => txn.id}
              estimateSize={56}
              overscan={10}
              ariaLabel={t('transactions.title')}
              renderHeader={renderHeader}
              renderRow={renderRow}
            />
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
