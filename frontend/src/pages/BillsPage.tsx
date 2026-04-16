import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { AddBillDialog } from '@/components/bills/AddBillDialog';
import { BillsCalendar } from '@/components/bills/BillsCalendar';
import { useBills, useCreateBill, useDeleteBill, usePayBill } from '@/hooks/useBills';
import { formatTRY } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import {
  Receipt,
  Calendar,
  CheckCircle2,
  AlertCircle,
  Trash2,
  CreditCard,
  TrendingUp,
  TrendingDown,
} from 'lucide-react';

function currentPeriod(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

export function BillsPage() {
  const { t } = useTranslation();

  const billsQuery = useBills();
  const createBill = useCreateBill();
  const deleteBill = useDeleteBill();
  const payBill = usePayBill();

  const bills = billsQuery.data ?? [];
  const activeBills = bills.filter((b) => b.isActive);
  const totalDue = activeBills.reduce((sum, b) => sum + b.amount, 0);
  const paidBills = activeBills.filter((b) => b.currentPeriodStatus === 'PAID');
  const paidTotal = paidBills.reduce((sum, b) => sum + b.amount, 0);
  const pendingBills = activeBills.filter((b) => b.currentPeriodStatus === 'PENDING');
  const pendingTotal = pendingBills.reduce((sum, b) => sum + b.amount, 0);

  const [confirmingDelete, setConfirmingDelete] = useState<string | null>(null);

  const handleDelete = (id: string) => {
    if (confirmingDelete === id) {
      deleteBill.mutate(id);
      setConfirmingDelete(null);
    } else {
      setConfirmingDelete(id);
      setTimeout(() => setConfirmingDelete(null), 3000);
    }
  };

  const handlePay = (billId: string) => {
    payBill.mutate({ id: billId, req: { period: currentPeriod() } });
  };

  return (
    <div className="space-y-6 max-w-[1200px]">
      <PageHeader
        title={t('bills.title')}
        description={t('bills.description')}
        actions={
          <AddBillDialog
            onSubmit={(req) => createBill.mutate(req)}
            isPending={createBill.isPending}
          />
        }
      />

      {/* KPI strip */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">{t('bills.dueThisMonth')}</p>
                <p className="text-2xl font-semibold font-mono tabular-nums tracking-tight">
                  {formatTRY(totalDue)}
                </p>
                <p className="text-xs text-muted-foreground">
                  {activeBills.length} {t('bills.totalBills')}
                </p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Calendar className="w-5 h-5 text-primary" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">{t('bills.paid')}</p>
                <p className="text-2xl font-semibold font-mono tabular-nums tracking-tight text-emerald-400">
                  {formatTRY(paidTotal)}
                </p>
                <p className="text-xs text-muted-foreground">
                  {paidBills.length} {t('bills.paidHint')}
                </p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center">
                <CheckCircle2 className="w-5 h-5 text-emerald-400" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-start justify-between">
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground font-medium">{t('bills.pending')}</p>
                <p className="text-2xl font-semibold font-mono tabular-nums tracking-tight text-amber-400">
                  {formatTRY(pendingTotal)}
                </p>
                <p className="text-xs text-muted-foreground">
                  {pendingBills.length} {t('bills.pendingHint')}
                </p>
              </div>
              <div className="w-10 h-10 rounded-lg bg-amber-500/10 flex items-center justify-center">
                <AlertCircle className="w-5 h-5 text-amber-400" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {bills.length > 0 && (
        <Card>
          <CardContent className="p-5">
            <BillsCalendar bills={bills} />
          </CardContent>
        </Card>
      )}

      {/* Bills list */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium">{t('bills.allBills')}</CardTitle>
        </CardHeader>
        <CardContent className="px-0">
          {bills.length === 0 ? (
            <EmptyState
              icon={Receipt}
              title={t('bills.emptyTitle')}
              description={t('bills.emptyDesc')}
              action={
                <AddBillDialog
                  onSubmit={(req) => createBill.mutate(req)}
                  isPending={createBill.isPending}
                />
              }
            />
          ) : (
            <div className="divide-y divide-border">
              {bills.map((bill) => {
                const isPaid = bill.currentPeriodStatus === 'PAID';
                const isUrgent = !isPaid && bill.daysUntilDue <= 3 && bill.daysUntilDue >= 0;
                return (
                  <div
                    key={bill.id}
                    className="flex items-center gap-4 px-6 py-3.5 group hover:bg-accent/30 transition-colors"
                  >
                    {/* Status indicator */}
                    <div
                      className={cn(
                        'w-2.5 h-2.5 rounded-full flex-shrink-0',
                        isPaid ? 'bg-emerald-400' : isUrgent ? 'bg-red-400 animate-pulse' : 'bg-amber-400'
                      )}
                    />

                    {/* Name + category */}
                    <div className="flex-1 min-w-0">
                      <p className={cn('text-sm font-medium', isPaid && 'line-through opacity-60')}>
                        {bill.name}
                      </p>
                      <p className="text-[11px] text-muted-foreground">
                        {bill.category && <span>{bill.category} -- </span>}
                        {t('bills.dueDayLabel', { day: bill.dueDay })}
                        {!isPaid && bill.daysUntilDue >= 0 && (
                          <span className={cn('ml-1.5', isUrgent && 'text-red-400 font-medium')}>
                            ({bill.daysUntilDue === 0
                              ? t('bills.dueToday')
                              : t('bills.daysLeft', { count: bill.daysUntilDue })})
                          </span>
                        )}
                      </p>
                    </div>

                    {/* Amount + variance */}
                    <div className="flex items-center gap-2">
                      {bill.variance && (
                        <span
                          title={t('bills.varianceVs', {
                            previous: formatTRY(bill.variance.previousAmount),
                            period: bill.variance.previousPeriod,
                          })}
                          className={cn(
                            'inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 text-[10px] font-mono tabular-nums',
                            bill.variance.flagged
                              ? bill.variance.delta > 0
                                ? 'bg-red-500/15 text-red-400'
                                : 'bg-emerald-500/15 text-emerald-400'
                              : 'bg-muted text-muted-foreground'
                          )}
                        >
                          {bill.variance.delta > 0 ? (
                            <TrendingUp className="w-3 h-3" />
                          ) : (
                            <TrendingDown className="w-3 h-3" />
                          )}
                          {bill.variance.deltaPercent > 0 ? '+' : ''}
                          {bill.variance.deltaPercent.toFixed(1)}%
                        </span>
                      )}
                      <span className="text-sm font-mono tabular-nums font-medium">
                        {formatTRY(bill.amount)}
                      </span>
                    </div>

                    {/* Pay button */}
                    {!isPaid && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 px-2.5 text-xs cursor-pointer"
                        onClick={() => handlePay(bill.id)}
                        disabled={payBill.isPending}
                      >
                        <CreditCard className="w-3.5 h-3.5 mr-1" />
                        {t('bills.markPaid')}
                      </Button>
                    )}
                    {isPaid && (
                      <span className="text-[11px] text-emerald-400 font-medium uppercase tracking-wider">
                        {t('bills.paid')}
                      </span>
                    )}

                    {/* Delete */}
                    <button
                      onClick={() => handleDelete(bill.id)}
                      className={cn(
                        'p-1 rounded transition-all cursor-pointer',
                        confirmingDelete === bill.id
                          ? 'opacity-100 bg-destructive/20 text-destructive'
                          : 'opacity-0 group-hover:opacity-100 hover:bg-destructive/10 text-destructive'
                      )}
                      title={confirmingDelete === bill.id ? t('common.confirmAgain') : t('common.delete')}
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
