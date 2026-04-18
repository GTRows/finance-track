import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { BellRing, BellOff, Loader2, Zap, ShieldAlert, CheckCircle2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { usePush } from '@/hooks/usePush';

export function PushNotificationSection() {
  const { t } = useTranslation();
  const { support, permission, subscribed, busy, subscribe, unsubscribe, sendTest } = usePush();

  if (support === 'checking') {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="w-4 h-4 animate-spin" />
        {t('common.loading')}
      </div>
    );
  }

  if (support === 'unsupported') {
    return (
      <div className="flex items-center gap-3 rounded-md border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-sm">
        <ShieldAlert className="w-4 h-4 text-amber-400 shrink-0" />
        <span className="text-muted-foreground">{t('settings.push.unsupported')}</span>
      </div>
    );
  }

  const denied = permission === 'denied';

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 rounded-md border border-border px-3 py-2.5">
        <div className="flex items-center gap-3 min-w-0">
          <div
            className={cn(
              'w-8 h-8 rounded-lg flex items-center justify-center shrink-0',
              subscribed ? 'bg-emerald-500/10 text-emerald-400' : 'bg-muted text-muted-foreground',
            )}
          >
            {subscribed ? <CheckCircle2 className="w-4 h-4" /> : <BellOff className="w-4 h-4" />}
          </div>
          <div className="min-w-0">
            <p className="text-sm font-medium">
              {subscribed ? t('settings.push.subscribed') : t('settings.push.notSubscribed')}
            </p>
            <p className="text-[11px] text-muted-foreground">
              {denied
                ? t('settings.push.denied')
                : subscribed
                  ? t('settings.push.subscribedHint')
                  : t('settings.push.notSubscribedHint')}
            </p>
          </div>
        </div>
        {subscribed ? (
          <Button
            size="sm"
            variant="outline"
            disabled={busy}
            onClick={unsubscribe}
            className="cursor-pointer"
          >
            {busy ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <BellOff className="w-3.5 h-3.5 mr-1.5" />}
            {t('settings.push.unsubscribe')}
          </Button>
        ) : (
          <Button
            size="sm"
            disabled={busy || denied}
            onClick={subscribe}
            className="cursor-pointer"
          >
            {busy ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <BellRing className="w-3.5 h-3.5 mr-1.5" />}
            {t('settings.push.subscribe')}
          </Button>
        )}
      </div>

      {subscribed && (
        <div className="flex items-center justify-between gap-3 rounded-md border border-border bg-muted/20 px-3 py-2.5">
          <div className="min-w-0">
            <p className="text-xs font-medium">{t('settings.push.testTitle')}</p>
            <p className="text-[11px] text-muted-foreground">{t('settings.push.testHint')}</p>
          </div>
          <Button
            size="sm"
            variant="outline"
            disabled={busy}
            onClick={sendTest}
            className="cursor-pointer"
          >
            {busy ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Zap className="w-3.5 h-3.5 mr-1.5" />
            )}
            {t('settings.push.sendTest')}
          </Button>
        </div>
      )}
    </div>
  );
}
