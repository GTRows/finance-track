import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { MailWarning, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuthStore } from '@/store/auth.store';
import { authApi } from '@/api/auth.api';

export function EmailVerificationBanner() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const [dismissed, setDismissed] = useState(false);
  const [status, setStatus] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle');

  if (!user || user.emailVerified || dismissed) return null;

  const resend = async () => {
    setStatus('sending');
    try {
      await authApi.resendEmailVerification();
      setStatus('sent');
    } catch {
      setStatus('error');
    }
  };

  return (
    <div
      role="alert"
      className="sticky top-0 z-30 flex items-center gap-2 border-b border-amber-500/30 bg-amber-500/10 px-4 py-2 text-sm text-amber-700 dark:text-amber-400"
    >
      <MailWarning className="h-4 w-4 flex-shrink-0" />
      <span className="flex-1">
        {t('auth.unverifiedBanner', { email: user.email })}
      </span>
      {status === 'sent' ? (
        <span className="text-xs text-amber-600 dark:text-amber-400">
          {t('auth.unverifiedSent')}
        </span>
      ) : (
        <Button
          size="sm"
          variant="ghost"
          className="h-7 px-2 cursor-pointer text-amber-700 dark:text-amber-400 hover:bg-amber-500/20"
          disabled={status === 'sending'}
          onClick={resend}
        >
          {status === 'sending' ? t('common.saving') : t('auth.unverifiedResend')}
        </Button>
      )}
      <button
        type="button"
        onClick={() => setDismissed(true)}
        aria-label={t('common.closeMenu')}
        className="ml-1 p-1 rounded hover:bg-amber-500/20 cursor-pointer"
      >
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
