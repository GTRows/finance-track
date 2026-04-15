import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Bell, Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  useMarkAllRead,
  useNotifications,
  useUnreadCount,
} from '@/hooks/useAlerts';
import { formatDateTime } from '@/utils/formatters';

export function NotificationBell() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const unreadQuery = useUnreadCount();
  const notificationsQuery = useNotifications();
  const markAllRead = useMarkAllRead();

  const unread = unreadQuery.data?.count ?? 0;
  const items = (notificationsQuery.data ?? []).slice(0, 10);

  useEffect(() => {
    if (!open) return;
    const onClickAway = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClickAway);
    return () => document.removeEventListener('mousedown', onClickAway);
  }, [open]);

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="relative w-9 h-9 rounded-md flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer"
        aria-label={t('alerts.notifications')}
      >
        <Bell className="w-4 h-4" />
        {unread > 0 && (
          <span className="absolute top-1 right-1 min-w-[16px] h-4 px-1 rounded-full bg-rose-500 text-[10px] font-semibold text-white flex items-center justify-center">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 rounded-lg border bg-popover shadow-lg z-30 overflow-hidden">
          <div className="flex items-center justify-between px-3 h-10 border-b">
            <span className="text-sm font-semibold">{t('alerts.notifications')}</span>
            {unread > 0 && (
              <button
                type="button"
                onClick={() => markAllRead.mutate()}
                disabled={markAllRead.isPending}
                className="text-xs text-primary hover:underline cursor-pointer disabled:opacity-50 inline-flex items-center gap-1"
              >
                <Check className="w-3 h-3" />
                {t('alerts.markAllRead')}
              </button>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto">
            {items.length === 0 ? (
              <div className="py-10 px-4 text-center text-xs text-muted-foreground">
                {t('alerts.noNotifications')}
              </div>
            ) : (
              <ul className="divide-y">
                {items.map((n) => (
                  <li
                    key={n.id}
                    className={cn(
                      'px-3 py-2.5 text-sm',
                      n.readAt == null && 'bg-primary/5'
                    )}
                  >
                    <div className="flex items-start gap-2">
                      <span
                        className={cn(
                          'mt-1.5 w-1.5 h-1.5 rounded-full flex-shrink-0',
                          n.readAt == null ? 'bg-primary' : 'bg-transparent'
                        )}
                      />
                      <div className="flex-1 min-w-0">
                        <div className="text-xs leading-relaxed">{n.message}</div>
                        <div className="text-[10px] text-muted-foreground mt-1 tabular-nums">
                          {formatDateTime(n.createdAt)}
                        </div>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <Link
            to="/alerts"
            onClick={() => setOpen(false)}
            className="flex items-center justify-center h-9 border-t text-xs text-primary hover:bg-accent transition-colors cursor-pointer"
          >
            {t('alerts.manageAll')}
          </Link>
        </div>
      )}
    </div>
  );
}
