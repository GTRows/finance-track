import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { ChevronLeft, ChevronRight, ShieldCheck, ShieldAlert } from 'lucide-react';
import { cn } from '@/lib/utils';
import { auditApi } from '@/api/audit.api';
import { formatDateTime } from '@/utils/formatters';

const PAGE_SIZE = 20;

export function AuditLogSection() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['audit-log', page],
    queryFn: () => auditApi.list({ page, size: PAGE_SIZE }),
    staleTime: 30_000,
  });

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">{t('common.loading')}</p>;
  }

  if (isError || !data) {
    return <p className="text-sm text-destructive">{t('audit.loadError')}</p>;
  }

  if (data.items.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('audit.empty')}</p>;
  }

  const totalPages = Math.max(data.totalPages, 1);

  return (
    <div className="space-y-3">
      <div className="border rounded-md overflow-hidden">
        <div className="max-h-[320px] overflow-y-auto">
          <table className="w-full text-xs">
            <thead className="bg-muted/50 text-muted-foreground sticky top-0">
              <tr>
                <th className="text-left font-medium px-3 py-2 w-8"></th>
                <th className="text-left font-medium px-3 py-2">{t('audit.time')}</th>
                <th className="text-left font-medium px-3 py-2">{t('audit.action')}</th>
                <th className="text-left font-medium px-3 py-2">{t('audit.user')}</th>
                <th className="text-left font-medium px-3 py-2">{t('audit.ip')}</th>
                <th className="text-left font-medium px-3 py-2">{t('audit.detail')}</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((entry) => {
                const success = entry.status === 'SUCCESS';
                const Icon = success ? ShieldCheck : ShieldAlert;
                return (
                  <tr key={entry.id} className="border-t">
                    <td className="px-3 py-2">
                      <Icon
                        className={cn(
                          'w-3.5 h-3.5',
                          success ? 'text-emerald-500' : 'text-destructive'
                        )}
                      />
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap text-muted-foreground">
                      {formatDateTime(entry.createdAt)}
                    </td>
                    <td className="px-3 py-2 font-medium">{entry.action}</td>
                    <td className="px-3 py-2">{entry.username ?? '-'}</td>
                    <td className="px-3 py-2 text-muted-foreground">{entry.ipAddress ?? '-'}</td>
                    <td className="px-3 py-2 text-muted-foreground truncate max-w-[240px]">
                      {entry.detail ?? ''}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>
          {t('audit.pageInfo', {
            page: data.page + 1,
            total: totalPages,
            count: data.totalElements,
          })}
        </span>
        <div className="flex gap-1">
          <Button
            variant="outline"
            size="sm"
            className="cursor-pointer"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
          >
            <ChevronLeft className="w-3.5 h-3.5" />
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="cursor-pointer"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            <ChevronRight className="w-3.5 h-3.5" />
          </Button>
        </div>
      </div>
    </div>
  );
}
