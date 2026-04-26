import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { KeyRound } from 'lucide-react';
import { authApi } from '@/api/auth.api';
import { useAuthStore } from '@/store/auth.store';
import type { ApiError } from '@/types/auth.types';

export function PasswordSection() {
  const { t } = useTranslation();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const [open, setOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const reset = () => {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setError(null);
  };

  const mutation = useMutation({
    mutationFn: (data: { currentPassword: string; newPassword: string }) =>
      authApi.changePassword(data),
    onSuccess: async () => {
      setSuccess(true);
      setError(null);
      setTimeout(() => {
        clearAuth();
      }, 1200);
    },
    onError: (err) => {
      const axiosError = err as AxiosError<ApiError>;
      const code = axiosError.response?.data?.code;
      if (code === 'PASSWORD_INVALID') {
        setError(t('settings.passwordCurrentWrong'));
      } else if (code === 'PASSWORD_UNCHANGED') {
        setError(t('settings.passwordUnchanged'));
      } else {
        setError(axiosError.response?.data?.error ?? t('common.somethingWentWrong'));
      }
    },
  });

  const newTooShort = newPassword.length > 0 && newPassword.length < 8;
  const mismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;
  const valid =
    currentPassword.length > 0 &&
    newPassword.length >= 8 &&
    newPassword === confirmPassword;

  const handleSubmit = () => {
    if (!valid) return;
    mutation.mutate({ currentPassword, newPassword });
  };

  return (
    <div className="border-t pt-4 space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 bg-muted text-muted-foreground">
            <KeyRound className="w-4 h-4" />
          </div>
          <div className="space-y-0.5">
            <p className="text-sm font-medium">{t('settings.password')}</p>
            <p className="text-xs text-muted-foreground">{t('settings.passwordDescription')}</p>
          </div>
        </div>
        {!open && (
          <Button
            size="sm"
            variant="outline"
            className="cursor-pointer flex-shrink-0"
            onClick={() => setOpen(true)}
          >
            {t('settings.changePassword')}
          </Button>
        )}
      </div>

      {open && !success && (
        <div className="rounded-lg border bg-muted/30 p-4 space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="current-pw">{t('settings.passwordCurrent')}</Label>
            <Input
              id="current-pw"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="new-pw">{t('settings.passwordNew')}</Label>
            <Input
              id="new-pw"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
            {newTooShort && (
              <p className="text-xs text-muted-foreground">{t('settings.passwordMinLength')}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="confirm-pw">{t('settings.passwordConfirm')}</Label>
            <Input
              id="confirm-pw"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
            {mismatch && (
              <p className="text-xs text-destructive">{t('settings.passwordMismatch')}</p>
            )}
          </div>

          {error && <p className="text-xs text-destructive">{error}</p>}

          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              className="cursor-pointer"
              onClick={() => {
                setOpen(false);
                reset();
              }}
            >
              {t('common.cancel')}
            </Button>
            <Button
              size="sm"
              className="cursor-pointer"
              disabled={!valid || mutation.isPending}
              onClick={handleSubmit}
            >
              {mutation.isPending ? t('common.saving') : t('settings.changePassword')}
            </Button>
          </div>
        </div>
      )}

      {success && (
        <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-xs text-emerald-600">
          {t('settings.passwordChangedSigningOut')}
        </div>
      )}
    </div>
  );
}
