import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { KeyRound, MailCheck, ShieldCheck, ShieldX, Loader2, ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { authApi } from '@/api/auth.api';
import type { ApiError } from '@/types/auth.types';

type RequestStatus = 'idle' | 'sending' | 'sent';
type ConfirmStatus = 'idle' | 'submitting' | 'success' | 'error';

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh flex items-center justify-center p-6 bg-muted/40">
      <div className="max-w-md w-full rounded-xl border bg-card shadow-sm p-8 space-y-4">
        {children}
      </div>
    </div>
  );
}

function RequestPanel() {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<RequestStatus>('idle');
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) return;
    setStatus('sending');
    setError(null);
    try {
      await authApi.requestPasswordReset(email);
      setStatus('sent');
    } catch (err) {
      const ax = err as AxiosError<ApiError>;
      setError(ax.response?.data?.error ?? t('common.somethingWentWrong'));
      setStatus('idle');
    }
  };

  return (
    <Shell>
      <div className="w-12 h-12 mx-auto rounded-full bg-primary/10 flex items-center justify-center">
        <KeyRound className="w-6 h-6 text-primary" />
      </div>
      <h1 className="text-lg font-semibold text-center">{t('auth.resetRequestTitle')}</h1>
      <p className="text-sm text-muted-foreground text-center">
        {t('auth.resetRequestBody')}
      </p>

      {status === 'sent' ? (
        <div className="rounded-md bg-emerald-500/10 border border-emerald-500/30 px-3 py-3 flex items-start gap-2">
          <MailCheck className="w-4 h-4 mt-0.5 text-emerald-500 flex-shrink-0" />
          <p className="text-sm text-emerald-700 dark:text-emerald-400">
            {t('auth.resetRequestSent')}
          </p>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="email">{t('auth.email')}</Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={t('auth.emailPlaceholder')}
              autoComplete="email"
              required
              autoFocus
            />
          </div>
          {error && (
            <div className="rounded-md bg-destructive/10 border border-destructive/20 px-3 py-2.5">
              <p className="text-sm text-destructive">{error}</p>
            </div>
          )}
          <Button
            type="submit"
            className="w-full cursor-pointer"
            disabled={status === 'sending' || email.length === 0}
          >
            {status === 'sending' ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <>
                {t('auth.resetRequestButton')}
                <ArrowRight className="w-4 h-4 ml-2" />
              </>
            )}
          </Button>
        </form>
      )}

      <div className="text-center">
        <Link
          to="/login"
          className="text-xs text-muted-foreground hover:text-foreground transition-colors"
        >
          {t('auth.verifyBackToLogin')}
        </Link>
      </div>
    </Shell>
  );
}

function ConfirmPanel({ token }: { token: string }) {
  const { t } = useTranslation();
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [status, setStatus] = useState<ConfirmStatus>('idle');
  const [error, setError] = useState<string | null>(null);

  const tooShort = newPassword.length > 0 && newPassword.length < 8;
  const mismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;
  const canSubmit =
    newPassword.length >= 8 && newPassword === confirmPassword && status !== 'submitting';

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setStatus('submitting');
    setError(null);
    try {
      await authApi.confirmPasswordReset({ token, newPassword });
      setStatus('success');
    } catch (err) {
      const ax = err as AxiosError<ApiError>;
      setError(ax.response?.data?.error ?? t('common.somethingWentWrong'));
      setStatus('error');
    }
  };

  if (status === 'success') {
    return (
      <Shell>
        <div className="w-12 h-12 mx-auto rounded-full bg-emerald-500/10 flex items-center justify-center">
          <ShieldCheck className="w-6 h-6 text-emerald-500" />
        </div>
        <h1 className="text-lg font-semibold text-center">{t('auth.resetSuccessTitle')}</h1>
        <p className="text-sm text-muted-foreground text-center">
          {t('auth.resetSuccessBody')}
        </p>
        <Button asChild className="w-full cursor-pointer">
          <Link to="/login">{t('auth.verifyBackToLogin')}</Link>
        </Button>
      </Shell>
    );
  }

  return (
    <Shell>
      <div className="w-12 h-12 mx-auto rounded-full bg-primary/10 flex items-center justify-center">
        <KeyRound className="w-6 h-6 text-primary" />
      </div>
      <h1 className="text-lg font-semibold text-center">{t('auth.resetConfirmTitle')}</h1>
      <p className="text-sm text-muted-foreground text-center">
        {t('auth.resetConfirmBody')}
      </p>

      <form onSubmit={submit} className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="newPassword">{t('settings.passwordNew')}</Label>
          <Input
            id="newPassword"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder={t('auth.passwordMinPlaceholder')}
            autoComplete="new-password"
            required
            minLength={8}
            autoFocus
          />
          {tooShort && (
            <p className="text-xs text-destructive">{t('settings.passwordMinLength')}</p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="confirmPassword">{t('settings.passwordConfirm')}</Label>
          <Input
            id="confirmPassword"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder={t('auth.confirmPasswordPlaceholder')}
            autoComplete="new-password"
            required
            minLength={8}
          />
          {mismatch && (
            <p className="text-xs text-destructive">{t('settings.passwordMismatch')}</p>
          )}
        </div>
        {error && (
          <div className="rounded-md bg-destructive/10 border border-destructive/20 px-3 py-2.5 flex items-start gap-2">
            <ShieldX className="w-4 h-4 mt-0.5 text-destructive flex-shrink-0" />
            <p className="text-sm text-destructive">{error}</p>
          </div>
        )}
        <Button type="submit" className="w-full cursor-pointer" disabled={!canSubmit}>
          {status === 'submitting' ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <>
              {t('auth.resetConfirmButton')}
              <ArrowRight className="w-4 h-4 ml-2" />
            </>
          )}
        </Button>
      </form>

      <div className="text-center">
        <Link
          to="/login"
          className="text-xs text-muted-foreground hover:text-foreground transition-colors"
        >
          {t('auth.verifyBackToLogin')}
        </Link>
      </div>
    </Shell>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  return token ? <ConfirmPanel token={token} /> : <RequestPanel />;
}
