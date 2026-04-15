import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { AddAlertDialog } from '@/components/alerts/AddAlertDialog';
import { useAlerts, useCreateAlert, useDeleteAlert } from '@/hooks/useAlerts';
import { formatCurrency, formatDateTime } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import { Bell, ArrowUp, ArrowDown, Trash2, CheckCircle2 } from 'lucide-react';

export function AlertsPage() {
  const { t } = useTranslation();
  const alertsQuery = useAlerts();
  const createAlert = useCreateAlert();
  const deleteAlert = useDeleteAlert();
  const [confirming, setConfirming] = useState<string | null>(null);

  const alerts = alertsQuery.data ?? [];

  const handleDelete = (id: string) => {
    if (confirming === id) {
      deleteAlert.mutate(id);
      setConfirming(null);
    } else {
      setConfirming(id);
      setTimeout(() => setConfirming(null), 3000);
    }
  };

  return (
    <div className="space-y-6 max-w-[1000px]">
      <PageHeader
        title={t('alerts.title')}
        description={t('alerts.description')}
        actions={
          <AddAlertDialog
            onSubmit={(req) => createAlert.mutate(req)}
            isPending={createAlert.isPending}
          />
        }
      />

      {alerts.length === 0 ? (
        <EmptyState
          icon={Bell}
          title={t('alerts.empty')}
          description={t('alerts.emptyDescription')}
        />
      ) : (
        <div className="space-y-2">
          {alerts.map((alert) => {
            const Arrow = alert.direction === 'ABOVE' ? ArrowUp : ArrowDown;
            const isTriggered = alert.status === 'TRIGGERED';
            return (
              <Card key={alert.id} className={cn(isTriggered && 'border-primary/40')}>
                <CardContent className="p-4 flex items-center gap-4">
                  <div
                    className={cn(
                      'w-9 h-9 rounded-md flex items-center justify-center flex-shrink-0',
                      isTriggered
                        ? 'bg-primary/15 text-primary'
                        : alert.direction === 'ABOVE'
                          ? 'bg-emerald-500/10 text-emerald-500'
                          : 'bg-rose-500/10 text-rose-500'
                    )}
                  >
                    {isTriggered ? (
                      <CheckCircle2 className="w-4 h-4" />
                    ) : (
                      <Arrow className="w-4 h-4" />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-sm">{alert.assetSymbol}</span>
                      <span className="text-xs text-muted-foreground truncate">
                        {alert.assetName}
                      </span>
                    </div>
                    <div className="text-xs text-muted-foreground mt-0.5">
                      {t(`alerts.direction.${alert.direction === 'ABOVE' ? 'above' : 'below'}`)}{' '}
                      <span className="font-mono tabular-nums text-foreground">
                        {formatCurrency(alert.thresholdTry, true)}
                      </span>
                      {alert.currentPriceTry != null && (
                        <>
                          {' '}-- {t('alerts.current')}:{' '}
                          <span className="font-mono tabular-nums text-foreground">
                            {formatCurrency(alert.currentPriceTry, true)}
                          </span>
                        </>
                      )}
                    </div>
                  </div>
                  <div className="text-right hidden sm:block">
                    <div
                      className={cn(
                        'text-xs font-medium',
                        isTriggered ? 'text-primary' : 'text-emerald-500'
                      )}
                    >
                      {t(`alerts.status.${alert.status.toLowerCase()}`)}
                    </div>
                    <div className="text-[11px] text-muted-foreground mt-0.5">
                      {formatDateTime(alert.triggeredAt ?? alert.createdAt)}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleDelete(alert.id)}
                    className={cn(
                      'w-8 h-8 rounded-md flex items-center justify-center transition-colors cursor-pointer',
                      confirming === alert.id
                        ? 'bg-rose-500/15 text-rose-500'
                        : 'text-muted-foreground hover:text-rose-500 hover:bg-rose-500/10'
                    )}
                    title={
                      confirming === alert.id ? t('alerts.confirmDelete') : t('common.delete')
                    }
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
