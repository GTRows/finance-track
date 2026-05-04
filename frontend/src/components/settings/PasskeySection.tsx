import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { KeyRound, Trash2, Plus, ShieldCheck } from 'lucide-react';
import {
  isWebAuthnSupported,
  useAuthenticators,
  useDeleteAuthenticator,
  useWebAuthnRegister,
} from '@/hooks/useWebAuthn';
import { formatDateTime } from '@/utils/formatters';
import type { ApiError } from '@/types/auth.types';
import { cn } from '@/lib/utils';

export function PasskeySection() {
  const { t } = useTranslation();
  const supported = isWebAuthnSupported();

  const list = useAuthenticators(supported);
  const registerMutation = useWebAuthnRegister();
  const deleteMutation = useDeleteAuthenticator();

  const [enrolling, setEnrolling] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const resetForm = () => {
    setEnrolling(false);
    setName('');
    setError(null);
  };

  const handleEnroll = async () => {
    if (name.trim().length === 0) {
      setError(t('settings.passkeyNameRequired'));
      return;
    }
    setError(null);
    try {
      await registerMutation.mutateAsync({ name: name.trim() });
      resetForm();
    } catch (err) {
      if (err instanceof DOMException && err.name === 'NotAllowedError') {
        setError(t('settings.passkeyEnrollCancelled'));
        return;
      }
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error ?? t('common.somethingWentWrong'));
    }
  };

  const handleDelete = (id: string) => {
    deleteMutation.mutate(id);
  };

  if (!supported) {
    return (
      <div className="border-t pt-4">
        <div className="flex items-start gap-3">
          <div className="w-9 h-9 rounded-lg bg-muted text-muted-foreground flex items-center justify-center flex-shrink-0">
            <KeyRound className="w-4 h-4" />
          </div>
          <div className="space-y-0.5">
            <p className="text-sm font-medium">{t('settings.passkeyTitle')}</p>
            <p className="text-xs text-muted-foreground">{t('settings.passkeyUnsupported')}</p>
          </div>
        </div>
      </div>
    );
  }

  const authenticators = list.data ?? [];
  const enrolled = authenticators.length > 0;

  return (
    <div className="border-t pt-4 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div
            className={cn(
              'w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0',
              enrolled ? 'bg-emerald-500/10 text-emerald-500' : 'bg-muted text-muted-foreground'
            )}
          >
            {enrolled ? (
              <ShieldCheck className="w-4 h-4" />
            ) : (
              <KeyRound className="w-4 h-4" />
            )}
          </div>
          <div className="space-y-0.5">
            <p className="text-sm font-medium">{t('settings.passkeyTitle')}</p>
            <p className="text-xs text-muted-foreground">{t('settings.passkeyDescription')}</p>
          </div>
        </div>
        {!enrolling && (
          <Button
            size="sm"
            variant="outline"
            className="cursor-pointer flex-shrink-0"
            onClick={() => {
              setEnrolling(true);
              setError(null);
            }}
          >
            <Plus className="w-3.5 h-3.5 mr-1.5" />
            {t('settings.passkeyAdd')}
          </Button>
        )}
      </div>

      {enrolling && (
        <div className="rounded-lg border bg-muted/30 p-4 space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="passkey-name">{t('settings.passkeyNameLabel')}</Label>
            <Input
              id="passkey-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('settings.passkeyNamePlaceholder')}
              maxLength={120}
              autoFocus
            />
          </div>
          {error && <p className="text-xs text-destructive">{error}</p>}
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              className="cursor-pointer"
              onClick={resetForm}
              disabled={registerMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              size="sm"
              className="cursor-pointer"
              disabled={registerMutation.isPending || name.trim().length === 0}
              onClick={() => {
                void handleEnroll();
              }}
            >
              {registerMutation.isPending
                ? t('settings.passkeyEnrolling')
                : t('settings.passkeyEnroll')}
            </Button>
          </div>
        </div>
      )}

      {list.isLoading ? (
        <p className="text-xs text-muted-foreground">{t('common.loading')}</p>
      ) : authenticators.length === 0 ? (
        <p className="text-xs text-muted-foreground">{t('settings.passkeyEmpty')}</p>
      ) : (
        <ul className="space-y-2">
          {authenticators.map((auth) => (
            <li
              key={auth.id}
              className="rounded-lg border bg-muted/20 px-3 py-2.5 flex items-start justify-between gap-3"
            >
              <div className="flex items-start gap-3 min-w-0">
                <div className="w-8 h-8 rounded-md bg-primary/10 text-primary flex items-center justify-center flex-shrink-0">
                  <KeyRound className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">
                    {auth.name ?? t('settings.passkeyUnnamed')}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {t('settings.passkeyAdded')} {formatDateTime(auth.createdAt)}
                    {auth.lastUsedAt && (
                      <>
                        {' · '}
                        {t('settings.passkeyLastUsed')} {formatDateTime(auth.lastUsedAt)}
                      </>
                    )}
                  </p>
                </div>
              </div>
              <Button
                size="sm"
                variant="ghost"
                className="cursor-pointer flex-shrink-0 text-rose-500 hover:text-rose-500 hover:bg-rose-500/10"
                disabled={deleteMutation.isPending}
                onClick={() => handleDelete(auth.id)}
              >
                <Trash2 className="w-3.5 h-3.5 mr-1.5" />
                {t('settings.passkeyRevoke')}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
