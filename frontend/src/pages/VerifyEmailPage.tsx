import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AxiosError } from 'axios';
import { MailCheck, MailX, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { authApi } from '@/api/auth.api';
import type { ApiError } from '@/types/auth.types';

type Status = 'verifying' | 'success' | 'error';

export function VerifyEmailPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get('token');

  const [status, setStatus] = useState<Status>('verifying');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setErrorMessage(t('auth.verifyMissingToken'));
      return;
    }
    let cancelled = false;
    authApi
      .confirmEmailVerification(token)
      .then(() => {
        if (!cancelled) setStatus('success');
      })
      .catch((err) => {
        if (cancelled) return;
        const ax = err as AxiosError<ApiError>;
        setErrorMessage(ax.response?.data?.error ?? t('common.somethingWentWrong'));
        setStatus('error');
      });
    return () => {
      cancelled = true;
    };
  }, [token, t]);

  return (
    <div className="min-h-dvh flex items-center justify-center p-6 bg-muted/40">
      <div className="max-w-md w-full rounded-xl border bg-card shadow-sm p-8 text-center space-y-4">
        {status === 'verifying' && (
          <>
            <Loader2 className="w-8 h-8 mx-auto animate-spin text-muted-foreground" />
            <h1 className="text-lg font-semibold">{t('auth.verifyInProgress')}</h1>
          </>
        )}
        {status === 'success' && (
          <>
            <div className="w-12 h-12 mx-auto rounded-full bg-emerald-500/10 flex items-center justify-center">
              <MailCheck className="w-6 h-6 text-emerald-500" />
            </div>
            <h1 className="text-lg font-semibold">{t('auth.verifySuccessTitle')}</h1>
            <p className="text-sm text-muted-foreground">{t('auth.verifySuccessBody')}</p>
            <Button asChild className="cursor-pointer">
              <Link to="/">{t('auth.verifyGoToApp')}</Link>
            </Button>
          </>
        )}
        {status === 'error' && (
          <>
            <div className="w-12 h-12 mx-auto rounded-full bg-destructive/10 flex items-center justify-center">
              <MailX className="w-6 h-6 text-destructive" />
            </div>
            <h1 className="text-lg font-semibold">{t('auth.verifyErrorTitle')}</h1>
            <p className="text-sm text-muted-foreground">{errorMessage}</p>
            <Button asChild variant="outline" className="cursor-pointer">
              <Link to="/login">{t('auth.verifyBackToLogin')}</Link>
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
