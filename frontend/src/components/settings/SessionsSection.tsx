import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Laptop, Smartphone, Monitor, LogOut, ShieldX } from 'lucide-react';
import { cn } from '@/lib/utils';
import { sessionsApi, type SessionInfo } from '@/api/sessions.api';
import { useAuthStore } from '@/store/auth.store';
import { formatDateTime } from '@/utils/formatters';

function describeDevice(ua: string | null): { icon: typeof Laptop; label: string } {
  if (!ua) return { icon: Monitor, label: 'Unknown device' };
  const lower = ua.toLowerCase();
  if (lower.includes('iphone') || lower.includes('android')) {
    const browser = lower.includes('chrome') ? 'Chrome' : lower.includes('safari') ? 'Safari' : 'Mobile';
    const os = lower.includes('iphone') ? 'iOS' : 'Android';
    return { icon: Smartphone, label: `${browser} on ${os}` };
  }
  if (lower.includes('ipad') || lower.includes('tablet')) {
    return { icon: Smartphone, label: 'Tablet' };
  }
  const browser = lower.includes('edg/')
    ? 'Edge'
    : lower.includes('firefox')
    ? 'Firefox'
    : lower.includes('chrome')
    ? 'Chrome'
    : lower.includes('safari')
    ? 'Safari'
    : 'Browser';
  const os = lower.includes('windows')
    ? 'Windows'
    : lower.includes('mac os')
    ? 'macOS'
    : lower.includes('linux')
    ? 'Linux'
    : '';
  return { icon: Laptop, label: os ? `${browser} on ${os}` : browser };
}

export function SessionsSection() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const refreshToken = useAuthStore((s) => s.refreshToken);

  const query = useQuery({
    queryKey: ['auth', 'sessions'],
    queryFn: () => sessionsApi.list(refreshToken),
    staleTime: 15_000,
  });

  const revokeMutation = useMutation({
    mutationFn: (id: string) => sessionsApi.revoke(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['auth', 'sessions'] }),
  });

  const revokeOthersMutation = useMutation({
    mutationFn: () => sessionsApi.revokeOthers(refreshToken!),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['auth', 'sessions'] }),
  });

  const sessions: SessionInfo[] = query.data ?? [];
  const othersCount = sessions.filter((s) => !s.current).length;

  if (query.isLoading) {
    return <p className="text-xs text-muted-foreground">{t('common.loading')}</p>;
  }

  return (
    <div className="border-t pt-4 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-medium">{t('settings.sessionsTitle')}</p>
          <p className="text-xs text-muted-foreground mt-0.5">
            {t('settings.sessionsDescription')}
          </p>
        </div>
        <Button
          size="sm"
          variant="outline"
          className="cursor-pointer flex-shrink-0"
          disabled={othersCount === 0 || revokeOthersMutation.isPending || !refreshToken}
          onClick={() => revokeOthersMutation.mutate()}
        >
          <ShieldX className="w-3.5 h-3.5 mr-1.5" />
          {t('settings.sessionsRevokeOthers')}
        </Button>
      </div>

      {sessions.length === 0 && (
        <p className="text-xs text-muted-foreground">{t('settings.sessionsEmpty')}</p>
      )}

      <ul className="space-y-2">
        {sessions.map((s) => {
          const { icon: Icon, label } = describeDevice(s.userAgent);
          return (
            <li
              key={s.id}
              className={cn(
                'rounded-lg border bg-muted/20 px-3 py-2.5 flex items-start justify-between gap-3',
                s.current && 'border-primary/40 bg-primary/5'
              )}
            >
              <div className="flex items-start gap-3 min-w-0">
                <div
                  className={cn(
                    'w-8 h-8 rounded-md flex items-center justify-center flex-shrink-0',
                    s.current ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground'
                  )}
                >
                  <Icon className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">
                    {label}
                    {s.current && (
                      <span className="ml-2 text-[10px] uppercase tracking-wide text-primary">
                        {t('settings.sessionsCurrent')}
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {s.ipAddress ?? t('common.unknown')} · {t('settings.sessionsLastUsed')}{' '}
                    {s.lastUsedAt ? formatDateTime(s.lastUsedAt) : '-'}
                  </p>
                </div>
              </div>
              {!s.current && (
                <Button
                  size="sm"
                  variant="ghost"
                  className="cursor-pointer flex-shrink-0 text-rose-500 hover:text-rose-500 hover:bg-rose-500/10"
                  disabled={revokeMutation.isPending}
                  onClick={() => revokeMutation.mutate(s.id)}
                >
                  <LogOut className="w-3.5 h-3.5 mr-1.5" />
                  {t('settings.sessionsRevoke')}
                </Button>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
