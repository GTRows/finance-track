import { AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useBackendHealth } from '@/hooks/useBackendHealth';

export function BackendStatusBanner() {
  const { t } = useTranslation();
  const { data, isLoading } = useBackendHealth();

  if (isLoading || data !== false) return null;

  return (
    <div
      role="alert"
      className="sticky top-0 z-40 flex items-center gap-2 border-b border-destructive/30 bg-destructive/10 px-4 py-2 text-sm text-destructive"
    >
      <AlertTriangle className="h-4 w-4 flex-shrink-0" />
      <span className="flex-1">{t('common.backendUnreachable')}</span>
    </div>
  );
}
