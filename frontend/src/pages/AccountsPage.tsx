import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { Archive, Loader2, Plus, Wallet } from 'lucide-react';
import { useAccounts, useAccountTotals } from '@/hooks/useAccounts';
import { AddAccountDialog } from '@/components/accounts/AddAccountDialog';
import { AccountListItem } from '@/components/accounts/AccountListItem';
import { ACCOUNT_TYPES } from '@/components/accounts/account-types';
import type { Account, AccountType } from '@/types/account.types';
import { formatCurrency } from '@/utils/formatters';

export function AccountsPage() {
  const { t } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Account | null>(null);

  const { data: accounts, isLoading, isError } = useAccounts();
  const { data: totals } = useAccountTotals();

  const liveCount = totals?.liveCount ?? accounts?.length ?? 0;
  const archivedCount = totals?.archivedCount ?? 0;

  const grouped = useMemo(() => {
    const map = new Map<AccountType, Account[]>();
    (accounts ?? []).forEach((account) => {
      const list = map.get(account.type) ?? [];
      list.push(account);
      map.set(account.type, list);
    });
    return ACCOUNT_TYPES.filter((meta) => map.has(meta.value)).map((meta) => ({
      meta,
      accounts: map.get(meta.value) ?? [],
    }));
  }, [accounts]);

  const balancesText = useMemo(() => {
    if (!totals || totals.byCurrency.length === 0) return '--';
    return totals.byCurrency
      .map((row) => {
        const amount = Number(row.totalBalance);
        if (Number.isFinite(amount)) {
          return formatCurrency(amount, false, row.currency);
        }
        return `${row.totalBalance} ${row.currency}`;
      })
      .join(' · ');
  }, [totals]);

  const openCreate = () => {
    setEditing(null);
    setDialogOpen(true);
  };

  const openEdit = (account: Account) => {
    setEditing(account);
    setDialogOpen(true);
  };

  const handleDialogChange = (next: boolean) => {
    setDialogOpen(next);
    if (!next) setEditing(null);
  };

  return (
    <div className="space-y-6 max-w-[1200px]">
      <PageHeader
        title={t('accounts.title')}
        description={t('accounts.description')}
        actions={
          <Button className="cursor-pointer" onClick={openCreate}>
            <Plus className="w-4 h-4 mr-2" />
            {t('accounts.addAccount')}
          </Button>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">
                  {t('accounts.liveAccounts')}
                </p>
                <p className="text-2xl font-semibold font-mono tracking-tight">{liveCount}</p>
                <p className="text-xs text-muted-foreground">
                  {liveCount === 0
                    ? t('accounts.empty.title')
                    : t('accounts.liveAccountsHint')}
                </p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Wallet className="w-5 h-5 text-primary" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">
                  {t('accounts.balances')}
                </p>
                <p className="text-lg font-semibold font-mono tracking-tight break-words">
                  {balancesText}
                </p>
                <p className="text-xs text-muted-foreground">{t('accounts.balancesHint')}</p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Wallet className="w-5 h-5 text-primary" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">
                  {t('accounts.archivedAccounts')}
                </p>
                <p className="text-2xl font-semibold font-mono tracking-tight">{archivedCount}</p>
                <p className="text-xs text-muted-foreground">{t('accounts.archivedHint')}</p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Archive className="w-5 h-5 text-primary" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="pb-2 flex-row items-center justify-between">
          <CardTitle className="text-sm font-medium">
            {t('accounts.yourAccounts')}
            {liveCount > 0 && ` (${liveCount})`}
          </CardTitle>
        </CardHeader>
        <CardContent className="px-0">
          {isLoading && (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
            </div>
          )}

          {isError && (
            <div className="px-6 py-12 text-center">
              <p className="text-sm text-destructive">{t('accounts.errors.failedToLoad')}</p>
            </div>
          )}

          {!isLoading && !isError && liveCount === 0 && (
            <EmptyState
              icon={Wallet}
              title={t('accounts.empty.title')}
              description={t('accounts.empty.description')}
              action={
                <Button className="cursor-pointer" onClick={openCreate}>
                  <Plus className="w-4 h-4 mr-2" />
                  {t('accounts.addAccount')}
                </Button>
              }
            />
          )}

          {!isLoading && !isError && liveCount > 0 && (
            <div>
              {grouped.map((group) => (
                <div key={group.meta.value}>
                  <div className="px-5 py-2 bg-muted/30 border-b">
                    <p className="text-[11px] uppercase tracking-wider font-medium text-muted-foreground">
                      {t(group.meta.labelKey)}
                    </p>
                  </div>
                  {group.accounts.map((account) => (
                    <AccountListItem key={account.id} account={account} onEdit={openEdit} />
                  ))}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <AddAccountDialog
        open={dialogOpen}
        onOpenChange={handleDialogChange}
        editing={editing}
      />
    </div>
  );
}
