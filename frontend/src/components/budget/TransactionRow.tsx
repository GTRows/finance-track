import { useTranslation } from 'react-i18next';
import { Trash2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatTRY } from '@/utils/formatters';
import type { BudgetTransaction } from '@/types/budget.types';
import { ReceiptAction } from './ReceiptAction';

interface TransactionRowProps {
  txn: BudgetTransaction;
  selected: boolean;
  anySelected: boolean;
  month: string;
  locale: string;
  onToggleSelect: (id: string) => void;
  onDelete: (id: string) => void;
}

export function TransactionRow({
  txn,
  selected,
  anySelected,
  month,
  locale,
  onToggleSelect,
  onDelete,
}: TransactionRowProps): JSX.Element {
  const { t } = useTranslation();

  return (
    <div
      role="cell"
      className={cn(
        'flex items-center gap-3 px-6 py-3 group transition-colors',
        selected ? 'bg-sky-500/[0.05]' : 'hover:bg-accent/30',
      )}
    >
      {/* Selection checkbox — revealed on hover unless any row is selected */}
      <button
        type="button"
        onClick={() => onToggleSelect(txn.id)}
        className={cn(
          'w-4 h-4 rounded border flex items-center justify-center cursor-pointer shrink-0 transition-opacity transition-colors',
          anySelected || selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100',
          selected
            ? 'bg-sky-500/20 border-sky-500/60 text-sky-300'
            : 'border-border hover:border-sky-500/40',
        )}
        title={t(selected ? 'budget.bulk.deselect' : 'budget.bulk.select')}
      >
        {selected && <span className="text-[9px] leading-none">✓</span>}
      </button>
      {/* Category dot */}
      <span
        className="w-2.5 h-2.5 rounded-full flex-shrink-0"
        style={{ backgroundColor: txn.categoryColor ?? 'hsl(var(--muted-foreground))' }}
      />

      {/* Description + category + tags */}
      <div className="flex-1 min-w-0">
        <p className="text-sm truncate">
          {txn.description || txn.categoryName || t('budget.uncategorized')}
        </p>
        <div className="flex items-center gap-1.5 flex-wrap mt-0.5">
          <span className="text-[11px] text-muted-foreground">
            {new Date(txn.txnDate).toLocaleDateString(locale, {
              day: 'numeric',
              month: 'short',
            })}
            {txn.categoryName && txn.description && (
              <span className="ml-1.5 opacity-70">{txn.categoryName}</span>
            )}
          </span>
          {txn.tags && txn.tags.length > 0 && txn.tags.map((tag) => (
            <span
              key={tag.id}
              className="inline-flex items-center gap-1 rounded-full bg-sky-500/10 border border-sky-500/30 px-1.5 py-px text-[10px] text-sky-300"
            >
              <span className="w-1 h-1 rounded-full" style={{ backgroundColor: tag.color ?? '#64748b' }} />
              {tag.name}
            </span>
          ))}
        </div>
      </div>

      {/* Amount */}
      <span
        className={cn(
          'text-sm font-mono tabular-nums font-medium',
          txn.txnType === 'INCOME' ? 'text-emerald-400' : 'text-red-400',
        )}
      >
        {txn.txnType === 'INCOME' ? '+' : '-'}
        {formatTRY(txn.amount)}
      </span>

      {/* Receipt */}
      <ReceiptAction
        transactionId={txn.id}
        hasReceipt={txn.hasReceipt}
        ocrStatus={txn.ocrStatus}
        ocrText={txn.ocrText}
        month={month}
      />

      {/* Delete */}
      <button
        onClick={() => onDelete(txn.id)}
        className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded hover:bg-destructive/10 cursor-pointer"
        title={t('common.delete')}
      >
        <Trash2 className="w-3.5 h-3.5 text-destructive" />
      </button>
    </div>
  );
}
