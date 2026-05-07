import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Archive, Loader2, MoreVertical, Pencil } from 'lucide-react';
import { useArchiveAccount } from '@/hooks/useAccounts';
import type { Account } from '@/types/account.types';
import { cn } from '@/lib/utils';
import { formatCurrency } from '@/utils/formatters';
import { getAccountTypeMeta } from './account-types';

interface AccountListItemProps {
  account: Account;
  onEdit: (account: Account) => void;
}

export function AccountListItem({ account, onEdit }: AccountListItemProps) {
  const { t } = useTranslation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const archive = useArchiveAccount();

  const meta = getAccountTypeMeta(account.type);
  const Icon = meta.icon;
  const balanceNumber = Number(account.currentBalance);
  const balance = Number.isFinite(balanceNumber)
    ? formatCurrency(balanceNumber, true, account.currency)
    : `${account.currentBalance} ${account.currency}`;

  const handleArchive = async () => {
    if (!confirming) {
      setConfirming(true);
      return;
    }
    try {
      await archive.mutateAsync(account.id);
    } finally {
      setConfirming(false);
      setMenuOpen(false);
    }
  };

  const handleEdit = () => {
    setMenuOpen(false);
    onEdit(account);
  };

  return (
    <div className="group flex items-center gap-4 px-5 py-4 border-b last:border-b-0 hover:bg-accent/30 transition-colors">
      <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
        <Icon className="w-5 h-5 text-primary" />
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-medium truncate">{account.name}</h3>
          <span
            className={cn(
              'text-[10px] font-medium uppercase tracking-wider px-1.5 py-0.5 rounded whitespace-nowrap',
              meta.badgeClass
            )}
          >
            {t(meta.labelKey)}
          </span>
        </div>
        {(account.institution || account.accountNumberSuffix) && (
          <p className="text-xs text-muted-foreground mt-0.5 truncate">
            {account.institution}
            {account.institution && account.accountNumberSuffix ? ' · ' : ''}
            {account.accountNumberSuffix ? `••••${account.accountNumberSuffix}` : ''}
          </p>
        )}
      </div>

      <div className="text-right hidden sm:block">
        <p className="text-sm font-mono">{balance}</p>
        <p className="text-xs text-muted-foreground">{account.currency}</p>
      </div>

      <div className="relative">
        <button
          type="button"
          onClick={() => setMenuOpen(!menuOpen)}
          className="w-8 h-8 rounded-md flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer opacity-0 group-hover:opacity-100 focus:opacity-100"
          aria-label={t('common.actions')}
        >
          <MoreVertical className="w-4 h-4" />
        </button>
        {menuOpen && (
          <>
            <div
              className="fixed inset-0 z-10"
              onClick={() => {
                setMenuOpen(false);
                setConfirming(false);
              }}
            />
            <div className="absolute right-0 top-9 z-20 min-w-[200px] rounded-md border bg-popover shadow-lg p-1">
              <button
                type="button"
                onClick={handleEdit}
                className="w-full flex items-center gap-2 px-2.5 py-2 text-sm hover:bg-accent rounded cursor-pointer transition-colors"
              >
                <Pencil className="w-4 h-4" />
                {t('accounts.actions.edit')}
              </button>
              <button
                type="button"
                onClick={handleArchive}
                disabled={archive.isPending}
                className="w-full flex items-center gap-2 px-2.5 py-2 text-sm text-destructive hover:bg-destructive/10 rounded cursor-pointer transition-colors disabled:opacity-50"
              >
                {archive.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Archive className="w-4 h-4" />
                )}
                {confirming ? t('common.confirmAgain') : t('accounts.actions.archive')}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
