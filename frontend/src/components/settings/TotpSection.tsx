import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import type { AxiosError } from 'axios';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Check, Copy, ShieldCheck, ShieldOff } from 'lucide-react';
import { authApi } from '@/api/auth.api';
import type { ApiError } from '@/types/auth.types';
import { cn } from '@/lib/utils';

function formatSecret(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim();
}

export function TotpSection() {
  const { t } = useTranslation();
  const qc = useQueryClient();

  const statusQuery = useQuery({
    queryKey: ['auth', 'totp', 'status'],
    queryFn: authApi.totpStatus,
  });

  const [mode, setMode] = useState<'idle' | 'enrolling' | 'disabling'>('idle');
  const [setup, setSetup] = useState<{ secret: string; otpauthUrl: string } | null>(null);
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const setupMutation = useMutation({
    mutationFn: authApi.totpSetup,
    onSuccess: (data) => {
      setSetup(data);
      setMode('enrolling');
      setError(null);
    },
    onError: (err) => {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error ?? t('common.somethingWentWrong'));
    },
  });

  const enableMutation = useMutation({
    mutationFn: (c: string) => authApi.totpEnable(c),
    onSuccess: () => {
      setMode('idle');
      setSetup(null);
      setCode('');
      setError(null);
      void qc.invalidateQueries({ queryKey: ['auth', 'totp', 'status'] });
    },
    onError: (err) => {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error ?? t('common.somethingWentWrong'));
    },
  });

  const disableMutation = useMutation({
    mutationFn: (p: string) => authApi.totpDisable(p),
    onSuccess: () => {
      setMode('idle');
      setPassword('');
      setError(null);
      void qc.invalidateQueries({ queryKey: ['auth', 'totp', 'status'] });
    },
    onError: (err) => {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error ?? t('common.somethingWentWrong'));
    },
  });

  useEffect(() => {
    if (!copied) return;
    const id = setTimeout(() => setCopied(false), 2000);
    return () => clearTimeout(id);
  }, [copied]);

  const enabled = statusQuery.data?.enabled ?? false;

  const handleCopySecret = async () => {
    if (!setup) return;
    try {
      await navigator.clipboard.writeText(setup.secret);
      setCopied(true);
    } catch {
      // clipboard can be blocked in non-secure contexts — user can still read the secret
    }
  };

  if (statusQuery.isLoading) {
    return <div className="text-xs text-muted-foreground">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div
            className={cn(
              'w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0',
              enabled ? 'bg-emerald-500/10 text-emerald-500' : 'bg-muted text-muted-foreground'
            )}
          >
            {enabled ? <ShieldCheck className="w-4 h-4" /> : <ShieldOff className="w-4 h-4" />}
          </div>
          <div className="space-y-0.5">
            <p className="text-sm font-medium">
              {enabled ? t('settings.totpEnabledLabel') : t('settings.totpDisabledLabel')}
            </p>
            <p className="text-xs text-muted-foreground">{t('settings.totpDescription')}</p>
          </div>
        </div>
        {mode === 'idle' && !enabled && (
          <Button
            size="sm"
            variant="outline"
            className="cursor-pointer flex-shrink-0"
            onClick={() => setupMutation.mutate()}
            disabled={setupMutation.isPending}
          >
            {t('settings.totpEnable')}
          </Button>
        )}
        {mode === 'idle' && enabled && (
          <Button
            size="sm"
            variant="outline"
            className="cursor-pointer flex-shrink-0 text-rose-500 border-rose-500/30 hover:bg-rose-500/10"
            onClick={() => setMode('disabling')}
          >
            {t('settings.totpDisable')}
          </Button>
        )}
      </div>

      {mode === 'enrolling' && setup && (
        <div className="rounded-lg border bg-muted/30 p-4 space-y-4">
          <div className="flex flex-col sm:flex-row gap-4 items-start">
            <div className="rounded-md bg-white p-3 flex-shrink-0">
              <QRCodeSVG value={setup.otpauthUrl} size={160} level="M" />
            </div>
            <div className="flex-1 space-y-3 min-w-0">
              <p className="text-xs text-muted-foreground">{t('settings.totpScanHint')}</p>
              <div className="space-y-1.5">
                <Label className="text-[10px] uppercase tracking-wide text-muted-foreground">
                  {t('settings.totpManualKey')}
                </Label>
                <div className="flex items-center gap-2">
                  <code className="flex-1 text-xs font-mono bg-background border rounded px-2 py-1.5 truncate">
                    {formatSecret(setup.secret)}
                  </code>
                  <button
                    type="button"
                    onClick={handleCopySecret}
                    className="w-7 h-7 rounded-md border flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer flex-shrink-0"
                    aria-label={t('settings.totpCopy')}
                  >
                    {copied ? (
                      <Check className="w-3.5 h-3.5 text-emerald-500" />
                    ) : (
                      <Copy className="w-3.5 h-3.5" />
                    )}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="totp-enable-code">{t('settings.totpConfirmCode')}</Label>
            <Input
              id="totp-enable-code"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              placeholder="123 456"
              className="font-mono tracking-[0.4em] text-center"
            />
          </div>

          {error && <p className="text-xs text-destructive">{error}</p>}

          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              className="cursor-pointer"
              onClick={() => {
                setMode('idle');
                setSetup(null);
                setCode('');
                setError(null);
              }}
            >
              {t('common.cancel')}
            </Button>
            <Button
              size="sm"
              className="cursor-pointer"
              disabled={code.length !== 6 || enableMutation.isPending}
              onClick={() => enableMutation.mutate(code)}
            >
              {enableMutation.isPending ? t('common.saving') : t('settings.totpActivate')}
            </Button>
          </div>
        </div>
      )}

      {mode === 'disabling' && (
        <div className="rounded-lg border bg-muted/30 p-4 space-y-3">
          <p className="text-xs text-muted-foreground">{t('settings.totpDisableHint')}</p>
          <div className="space-y-1.5">
            <Label htmlFor="totp-disable-pw">{t('auth.password')}</Label>
            <Input
              id="totp-disable-pw"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>
          {error && <p className="text-xs text-destructive">{error}</p>}
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              className="cursor-pointer"
              onClick={() => {
                setMode('idle');
                setPassword('');
                setError(null);
              }}
            >
              {t('common.cancel')}
            </Button>
            <Button
              size="sm"
              className="cursor-pointer bg-rose-500 hover:bg-rose-500/90"
              disabled={!password || disableMutation.isPending}
              onClick={() => disableMutation.mutate(password)}
            >
              {disableMutation.isPending ? t('common.saving') : t('settings.totpDisable')}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
